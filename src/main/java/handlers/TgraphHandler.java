package handlers;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;

import io.javalin.http.Context;
import tgraph.Tgraph;

public class TgraphHandler {
    // 声明并初始化 databasePaths
    private static final Map<String, String> databasePaths = new HashMap<>();
    private static final String PATHS_FILE = "config/database-paths.properties";
    
    // 静态初始化块，加载数据库路径
    static {
        loadDatabasePaths();
    }
    
    public TgraphHandler() {
    }
    
    // 加载数据库路径信息
    private static void loadDatabasePaths() {
        File file = new File(PATHS_FILE);
        if (!file.exists()) {
            // 如果文件不存在，尝试创建目录
            file.getParentFile().mkdirs();
            return; // 首次运行，无需加载
        }
        
        Properties props = new Properties();
        try (FileInputStream fis = new FileInputStream(file)) {
            props.load(fis);
            
            // 清空现有映射并重新加载
            databasePaths.clear();
            for (String dbName : props.stringPropertyNames()) {
                databasePaths.put(dbName, props.getProperty(dbName));
            }
            
            System.out.println("成功加载数据库路径信息: " + databasePaths.size() + " 个数据库");
        } catch (IOException e) {
            System.err.println("加载数据库路径信息时出错: " + e.getMessage());
        }
    }
    
    // 保存数据库路径信息
    private static void saveDatabasePaths() {
        Properties props = new Properties();
        
        // 将映射转换为属性
        for (Map.Entry<String, String> entry : databasePaths.entrySet()) {
            props.setProperty(entry.getKey(), entry.getValue());
        }
        
        try (FileOutputStream fos = new FileOutputStream(PATHS_FILE)) {
            props.store(fos, "TGraph Database Paths");
            System.out.println("成功保存数据库路径信息: " + databasePaths.size() + " 个数据库");
        } catch (IOException e) {
            System.err.println("保存数据库路径信息时出错: " + e.getMessage());
        }
    }
    
    // 创建数据库 - 修改以记录路径并持久化
    public void createDatabase(Context ctx) {
        String databaseName = ctx.pathParam("databaseName");
        if (Tgraph.graphDb != null) {
            Map<String, Object> errorResponse = new HashMap<>();
            List<Map<String, String>> errors = new ArrayList<>();
            Map<String, String> error = new HashMap<>();
            error.put("message", "已有数据库在运行，请先关闭当前数据库");
            error.put("code", "Neo.ClientError.General.DatabaseError");
            errors.add(error);
            errorResponse.put("errors", errors);
            ctx.status(409).json(errorResponse); // 409 Conflict
            return;
        }
        try {
            Tgraph.graphDb = Tgraph.createDb(databaseName);
            // 记录数据库路径并持久化
            String dbPath = Tgraph.TARGET_DIR + "/" + databaseName;
            databasePaths.put(databaseName, dbPath);
            saveDatabasePaths(); // 保存到文件
        } catch (Exception e) {
            e.printStackTrace();
        }
        ctx.status(201);
    }

    // 启动数据库 - 确保也记录路径
    public void startDatabase(Context ctx) {
        String databaseName = ctx.pathParam("databaseName");
        if (Tgraph.graphDb != null) {
            Map<String, Object> errorResponse = new HashMap<>();
            List<Map<String, String>> errors = new ArrayList<>();
            Map<String, String> error = new HashMap<>();
            error.put("message", "已有数据库在运行，请先关闭当前数据库");
            error.put("code", "Neo.ClientError.General.DatabaseError");
            errors.add(error);
            errorResponse.put("errors", errors);
            ctx.status(409).json(errorResponse); // 409 Conflict
            return;
        }
        Tgraph.graphDb = Tgraph.startDb(databaseName);
        
        // 如果数据库路径尚未记录，则记录并持久化
        if (!databasePaths.containsKey(databaseName)) {
            String dbPath = Tgraph.TARGET_DIR + "/" + databaseName;
            databasePaths.put(databaseName, dbPath);
            saveDatabasePaths(); // 保存到文件
        }
        
        ctx.status(201);
    }

    // 删除数据库 - 同时从路径记录中移除
    public void deleteDatabase(Context ctx) {
        String databaseName = ctx.pathParam("databaseName");
        boolean isDelete = Tgraph.deleteDb(databaseName);
        if (isDelete) {
            // 从路径记录中移除并持久化
            databasePaths.remove(databaseName);
            saveDatabasePaths();
            ctx.status(204);
        } else {
            Map<String, Object> errorResponse = new HashMap<>();
            List<Map<String, String>> errors = new ArrayList<>();
            Map<String, String> error = new HashMap<>();
            error.put("message", "删除数据库 '" + databaseName + "' 失败");
            error.put("code", "Neo.ClientError.General.DatabaseError");
            errors.add(error);
            errorResponse.put("errors", errors);
            ctx.status(500).json(errorResponse);
        }
    }

    // 关闭数据库 由于一个时间只能有一个数据库被打开 所以不用传入{databaseName}
    public void shutdownDatabase(Context ctx) {
        Tgraph.shutDown(Tgraph.graphDb);
        Tgraph.graphDb = null;
        ctx.status(204);
    }

    public void backupDatabase(Context ctx) {
        String databaseName = ctx.pathParam("databaseName");
        try {
            Tgraph.backupDatabase(databaseName);
            ctx.status(201);
        } catch (IllegalStateException e) {
            // 数据库正在运行的错误处理
            Map<String, Object> errorResponse = new HashMap<>();
            List<Map<String, String>> errors = new ArrayList<>();
            Map<String, String> error = new HashMap<>();
            error.put("message", e.getMessage());
            error.put("code", "Neo.ClientError.General.DatabaseError");
            errors.add(error);
            errorResponse.put("errors", errors);
            ctx.status(409).json(errorResponse);
        } catch (IllegalArgumentException e) {
            // 数据库不存在的错误处理
            Map<String, Object> errorResponse = new HashMap<>();
            List<Map<String, String>> errors = new ArrayList<>();
            Map<String, String> error = new HashMap<>();
            error.put("message", e.getMessage());
            error.put("code", "Neo.ClientError.General.DatabaseNotFound");
            errors.add(error);
            errorResponse.put("errors", errors);
            ctx.status(404).json(errorResponse);
        } catch (IOException e) {
            // IO错误处理
            Map<String, Object> errorResponse = new HashMap<>();
            List<Map<String, String>> errors = new ArrayList<>();
            Map<String, String> error = new HashMap<>();
            error.put("message", "备份数据库时发生错误: " + e.getMessage());
            error.put("code", "Neo.ClientError.General.IOError");
            errors.add(error);
            errorResponse.put("errors", errors);
            ctx.status(500).json(errorResponse);
        }
    }

    public void restoreDatabase(Context ctx) {
        String databaseName = ctx.pathParam("databaseName");
        try {
            Tgraph.restoreDatabase(databaseName);
            ctx.status(201);
        } catch (IllegalStateException e) {
            // 数据库正在运行或目标数据库已存在的错误处理
            Map<String, Object> errorResponse = new HashMap<>();
            List<Map<String, String>> errors = new ArrayList<>();
            Map<String, String> error = new HashMap<>();
            error.put("message", e.getMessage());
            error.put("code", "Neo.ClientError.General.DatabaseError");
            errors.add(error);
            errorResponse.put("errors", errors);
            ctx.status(409).json(errorResponse);
        } catch (IllegalArgumentException e) {
            // 备份文件不存在的错误处理
            Map<String, Object> errorResponse = new HashMap<>();
            List<Map<String, String>> errors = new ArrayList<>();
            Map<String, String> error = new HashMap<>();
            error.put("message", e.getMessage());
            error.put("code", "Neo.ClientError.General.BackupNotFound");
            errors.add(error);
            errorResponse.put("errors", errors);
            ctx.status(404).json(errorResponse);
        } catch (IOException e) {
            // IO错误处理
            Map<String, Object> errorResponse = new HashMap<>();
            List<Map<String, String>> errors = new ArrayList<>();
            Map<String, String> error = new HashMap<>();
            error.put("message", "恢复数据库时发生错误: " + e.getMessage());
            error.put("code", "Neo.ClientError.General.IOError");
            errors.add(error);
            errorResponse.put("errors", errors);
            ctx.status(500).json(errorResponse);
        }
    }

    // 获取数据库路径
    public void getDatabasePath(Context ctx) {
        String databaseName = ctx.pathParam("databaseName");
        String path = databasePaths.get(databaseName);
        
        if (path != null) {
            Map<String, Object> response = new HashMap<>();
            response.put("database", databaseName);
            response.put("path", path);
            ctx.status(200).json(response);
        } else {
            Map<String, Object> errorResponse = new HashMap<>();
            List<Map<String, String>> errors = new ArrayList<>();
            Map<String, String> error = new HashMap<>();
            error.put("message", "数据库 '" + databaseName + "' 不存在或未记录路径");
            error.put("code", "Neo.ClientError.General.DatabaseNotFound");
            errors.add(error);
            errorResponse.put("errors", errors);
            ctx.status(404).json(errorResponse);
        }
    }                    
    
    // 获取数据库状态
    public void getDatabaseStatus(Context ctx) {
        String databaseName = ctx.pathParam("databaseName");
        String dbDir = new File(Tgraph.TARGET_DIR, databaseName).getPath();
        Map<String, Object> response = new HashMap<>();
        
        try {
            // 尝试打开数据库
            GraphDatabaseService graphDb = new GraphDatabaseFactory()
                .newEmbeddedDatabaseBuilder(dbDir)
                .newGraphDatabase();
            // 能成功打开说明数据库没有被其他进程使用（关闭状态）
            graphDb.shutdown();
            
            // 返回关闭状态
            response.put("status", "stopped");
            ctx.status(200).json(response);
        } catch (Exception e) {
            // 捕获异常意味着数据库可能正在被其他进程使用（启动状态）
            response.put("status", "running");
            ctx.status(200).json(response);
        }
    }
}
