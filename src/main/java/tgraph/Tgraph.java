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
    //private static final String DB_PATH = "target/neo4j-hello-db";

    public GraphDatabaseService createDb(String DB_PATH) throws IOException {
        FileUtils.deleteRecursively( new File( DB_PATH ) );
        // START SNIPPET: startDb
        GraphDatabaseService graphDb = new GraphDatabaseFactory().newEmbeddedDatabase( DB_PATH );
        registerShutdownHook( graphDb );
        return graphDb;
        // END SNIPPET: startDb
    }

    public GraphDatabaseService startDb(String DB_PATH)
    {
        // START SNIPPET: startDb
        GraphDatabaseService graphDb = new GraphDatabaseFactory().newEmbeddedDatabase( DB_PATH );
        registerShutdownHook( graphDb );
        return graphDb;
        // END SNIPPET: startDb
    }

    public void shutDown(GraphDatabaseService graphDb)
    {
        // START SNIPPET: shutdownServer
        graphDb.shutdown();
        // END SNIPPET: shutdownServer
    }

    // START SNIPPET: shutdownHook
    private static void registerShutdownHook( final GraphDatabaseService graphDb )
    {
        // Registers a shutdown hook for the Neo4j instance so that it
        // shuts down nicely when the VM exits (even if you "Ctrl-C" the
        // running application).
        Runtime.getRuntime().addShutdownHook( new Thread()
        {
            @Override
            public void run()
            {
                graphDb.shutdown();
            }
        } );
    }
    // END SNIPPET: shutdownHook

    /**
     * 备份数据库
     * @param dbName 数据库名称
     * @throws IOException 如果备份过程中发生IO错误
     * @throws IllegalStateException 如果数据库正在运行
     */
    public void backupDatabase(String dbName) throws IOException {
        // 确保使用相对于target目录的路径
        String targetPath = "target";
        File dbDir = new File(targetPath, dbName);
        if (!dbDir.exists()) {
            throw new IllegalArgumentException("数据库 '" + dbDir.getPath() + "' 不存在");
        }

        // 检查数据库是否正在运行？  不知道是否正确
        File lockFile = new File(dbDir, "lock");
        if (lockFile.exists()) {
            throw new IllegalStateException("数据库正在运行，无法执行备份操作");
        }

        // 创建备份目录
        File backupDir = new File(targetPath, "backup");
        if (!backupDir.exists()) {
            backupDir.mkdirs();
        }

        // 生成备份文件名（包含时间戳）
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd_HHmmss");
        String timestamp = dateFormat.format(new Date());
        String backupFileName = dbName.replace('/', '_').replace('\\', '_') 
            + "_" + timestamp + ".zip";
        File backupFile = new File(backupDir, backupFileName);

        // 使用zip4j压缩文件夹
        try (ZipFile zipFile = new ZipFile(backupFile)) {
            zipFile.addFolder(dbDir);
        }
        // System.out.println("数据库备份完成: " + backupFile.getAbsolutePath());
    }

     /**
     * 从备份文件恢复数据库
     * @param backupFileName 备份文件的完整名称（包含时间戳）
     * @throws IOException 如果恢复过程中发生IO错误
     * @throws IllegalStateException 如果目标数据库已存在或正在运行
     */
    public void restoreDatabase(String backupFileName) throws IOException {
        // 确保使用相对于target目录的路径
        String targetPath = "target";
        File backupFile = new File(targetPath + "/backup", backupFileName);
        
        if (!backupFile.exists()) {
            throw new IllegalArgumentException("备份文件 '" + backupFile.getPath() + "' 不存在");
        }
        
        // 从备份文件名中提取数据库名称（去除时间戳部分）
        // 例如: neo4j-hello-db_20240225_143045.zip -> neo4j-hello-db
        String dbName = backupFileName.replaceAll("_\\d{8}_\\d{6}\\.zip$", "");
        
        // 检查目标数据库目录
        File dbDir = new File(targetPath, dbName);
        // 如果数据库目录存在，则抛出异常
        if (dbDir.exists()) {
            throw new IllegalStateException("目标数据库 '" + dbName + "' 已存在，请先删除或重命名");
        }
        
        // 解压备份文件到目标目录
        try (ZipFile zipFile = new ZipFile(backupFile)) {
            zipFile.extractAll(targetPath);
            // System.out.println("数据库已恢复到: " + dbDir.getPath());
        } catch (Exception e) {
            throw new IOException("恢复数据库失败: " + e.getMessage(), e);
        }
    }
}
