package tgraph;

import org.neo4j.dbms.api.DatabaseManagementService;
import org.neo4j.dbms.api.DatabaseManagementServiceBuilder;
import org.neo4j.graphdb.*;
import org.neo4j.io.fs.FileUtils;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.nio.file.Files;
import java.util.Comparator;

import net.lingala.zip4j.ZipFile;

public class Tgraph {
    // 静态全局变量，只需要一个实例
    public static final String TARGET_DIR = "target";

    //public static GraphDatabaseService graphDb = null;

    public static DatabaseManagementService graphDb = null;
    
    // 私有构造函数，防止实例化
    private Tgraph() {
        // 防止实例化的私有构造函数
    }
    
    /**
     * 获取用户数据库路径
     */
    private static String getUserDbPath(String username, String dbName) {
        return TARGET_DIR + File.separator + username + File.separator + dbName;
    }

    // 获取当前数据库的名字
    public static String getCurrentDbName() {
        // TODO: 这个获取的数据库名字永远是neo4j，后续如果要用的话，需要改成获取当前数据库的名字
        String absolutePath =  graphDb.database("neo4j").toString(); // 是绝对路径Community [D:\Desktop\study\graduation_project\source\test\target\neo4j-hello-db]
        // 取最后一个
        String[] parts = absolutePath.split(File.separator);
        return parts[parts.length - 1]; // neo4j-hello-db
    }
    
    /**
     * 创建数据库
     */
    public static DatabaseManagementService createDb(String username, String dbName) throws IOException {
        String dbPath = getUserDbPath(username, dbName);
        // 确保用户目录存在
        new File(TARGET_DIR + File.separator + username).mkdirs();
        deleteDirectoryRecursively(new File(dbPath));
        graphDb = new DatabaseManagementServiceBuilder(new File(dbPath).toPath()).build();
        registerShutdownHook(graphDb);
        return graphDb;
    }

    private static void deleteDirectoryRecursively(File directory) throws IOException {
        if (directory.isDirectory()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    deleteDirectoryRecursively(file);
                }
            }
        }
        if (!directory.delete()) {
            throw new IOException("无法删除文件或目录: " + directory.getAbsolutePath());
        }
    }

    /**
     * 删除数据库
     */
    public static boolean deleteDb(String username, String dbName) {
        String dbPath = getUserDbPath(username, dbName);
        try {
            deleteDirectoryRecursively(new File(dbPath));
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 启动数据库
     */
    public static DatabaseManagementService startDb(String username, String dbName) {
        String dbPath = getUserDbPath(username, dbName);
        graphDb = new DatabaseManagementServiceBuilder(new File(dbPath).toPath()).build();
        registerShutdownHook(graphDb);
        return graphDb;
    }

    /**
     * 关闭数据库
     */
    public static void shutDown() {
        if (graphDb != null) {
            graphDb.shutdown();
        }
    }

    /**
     * 注册JVM关闭钩子
     */
    private static void registerShutdownHook(final DatabaseManagementService graphDb) {
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                graphDb.shutdown();
            }
        });
    }

    /**
     * 备份数据库
     */
    public static void backupDatabase(String username, String dbName) throws IOException {
        File dbDir = new File(getUserDbPath(username, dbName));
        if (!dbDir.exists()) {
            throw new IllegalArgumentException("数据库 '" + dbDir.getPath() + "' 不存在");
        }

        // 检查数据库是否正在运行
        try {
            DatabaseManagementService graphDb = new DatabaseManagementServiceBuilder(dbDir.toPath()).build();
            graphDb.shutdown();
        } catch (Exception e) {
            throw new IllegalStateException("数据库正在运行，无法执行备份操作", e);
        }

        // 创建备份目录
        File backupDir = new File(TARGET_DIR, "backup");
        if (!backupDir.exists()) {
            backupDir.mkdirs();
        }

        // 生成备份文件名
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd_HHmmss");
        String timestamp = dateFormat.format(new Date());
        String backupFileName = username + "_" + dbName.replace('/', '_').replace('\\', '_') 
            + "_" + timestamp + ".zip";
        File backupFile = new File(backupDir, backupFileName);

        // 压缩数据库目录
        try (ZipFile zipFile = new ZipFile(backupFile)) {
            zipFile.addFolder(dbDir);
        }
    }

    /**
     * 从备份文件恢复数据库
     */
    public static void restoreDatabase(String username, String backupFileName) throws IOException {
        File backupFile = new File(TARGET_DIR + "/backup", backupFileName);
        
        if (!backupFile.exists()) {
            throw new IllegalArgumentException("备份文件 '" + backupFile.getPath() + "' 不存在");
        }
        
        // 提取数据库名称，格式应为username_dbname_yyyyMMdd_HHmmss.zip
        String fileNameWithoutExt = backupFileName.substring(0, backupFileName.lastIndexOf('.'));
        String[] parts = fileNameWithoutExt.split("_");
        if (parts.length < 4) {
            throw new IllegalArgumentException("备份文件名格式错误，应为username_dbname_yyyyMMdd_HHmmss.zip");
        }
        
        // 确认用户名匹配
        if (!parts[0].equals(username)) {
            throw new IllegalArgumentException("无权访问其他用户的备份文件");
        }
        
        // 重建数据库名
        String dbName = parts[1];
        for (int i = 2; i < parts.length - 2; i++) {
            dbName += "_" + parts[i];
        }
        
        // 确保用户目录存在
        new File(TARGET_DIR + File.separator + username).mkdirs();
        
        // 检查目标数据库目录
        File dbDir = new File(getUserDbPath(username, dbName));
        if (dbDir.exists()) {
            deleteDirectoryRecursively(dbDir);
            System.out.println("已删除原数据库文件夹: " + dbDir.getPath());
        }
        if (dbDir.exists()) {
            throw new IllegalStateException("目标数据库 '" + dbName + "' 已存在，请先删除或重命名");
        }
        
        // 解压备份文件
        try (ZipFile zipFile = new ZipFile(backupFile)) {
            zipFile.extractAll(TARGET_DIR + File.separator + username);
        } catch (Exception e) {
            throw new IOException("恢复数据库失败: " + e.getMessage(), e);
        }
    }
}
