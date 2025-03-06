package tgraph;

import org.neo4j.graphdb.*;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;
import org.neo4j.io.fs.FileUtils;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

import net.lingala.zip4j.ZipFile;

public class Tgraph {
    // 静态全局变量，只需要一个实例
    public static final String TARGET_DIR = "target";

    public static GraphDatabaseService graphDb = null;
    
    // 私有构造函数，防止实例化
    private Tgraph() {
        // 防止实例化的私有构造函数
    }
    
    /**
     * 创建数据库
     */
    public static GraphDatabaseService createDb(String dbName) throws IOException {
        String dbPath = TARGET_DIR + "/" + dbName;
        FileUtils.deleteRecursively(new File(dbPath));
        GraphDatabaseService graphDb = new GraphDatabaseFactory()
            .newEmbeddedDatabaseBuilder(new File(dbPath))
            .newGraphDatabase();
        registerShutdownHook(graphDb);
        return graphDb;
    }

    /**
     * 删除数据库
     */
    public static boolean deleteDb(String dbName) {
        String dbPath = TARGET_DIR + "/" + dbName;
        try {
            FileUtils.deleteRecursively(new File(dbPath));
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 启动数据库
     */
    public static GraphDatabaseService startDb(String dbName) {
        String dbPath = TARGET_DIR + "/" + dbName;
        GraphDatabaseService graphDb = new GraphDatabaseFactory()
            .newEmbeddedDatabaseBuilder(new File(dbPath))
            .newGraphDatabase();
        registerShutdownHook(graphDb);
        return graphDb;
    }

    /**
     * 关闭数据库
     */
    public static void shutDown(GraphDatabaseService graphDb) {
        if (graphDb != null) {
            graphDb.shutdown();
        }
    }

    /**
     * 注册JVM关闭钩子
     */
    private static void registerShutdownHook(final GraphDatabaseService graphDb) {
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
    public static void backupDatabase(String dbName) throws IOException {
        File dbDir = new File(TARGET_DIR, dbName);
        if (!dbDir.exists()) {
            throw new IllegalArgumentException("数据库 '" + dbDir.getPath() + "' 不存在");
        }

        // 检查数据库是否正在运行
        try {
            GraphDatabaseService graphDb = new GraphDatabaseFactory()
                .newEmbeddedDatabaseBuilder(dbDir)
                .newGraphDatabase();
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
        String backupFileName = dbName.replace('/', '_').replace('\\', '_') 
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
    public static void restoreDatabase(String backupFileName) throws IOException {
        File backupFile = new File(TARGET_DIR + "/backup", backupFileName);
        
        if (!backupFile.exists()) {
            throw new IllegalArgumentException("备份文件 '" + backupFile.getPath() + "' 不存在");
        }
        
        // 提取数据库名称
        String dbName = backupFileName.replaceAll("_\\d{8}_\\d{6}\\.zip$", "");
        
        // 检查目标数据库目录
        File dbDir = new File(TARGET_DIR, dbName);
        if (dbDir.exists()) {
            throw new IllegalStateException("目标数据库 '" + dbName + "' 已存在，请先删除或重命名");
        }
        
        // 解压备份文件
        try (ZipFile zipFile = new ZipFile(backupFile)) {
            zipFile.extractAll(TARGET_DIR);
        } catch (Exception e) {
            throw new IOException("恢复数据库失败: " + e.getMessage(), e);
        }
    }
}
