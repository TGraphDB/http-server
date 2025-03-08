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
import service.User;

public class TgraphHandler {
    // 声明并初始化 databasePaths，现在键为 "username:databaseName"
    private static final Map<String, String> databasePaths = new HashMap<>();
    private static final String PATHS_FILE = "config/database-paths.properties";
    
    // 静态初始化块，加载数据库路径
    static {
        loadDatabasePaths();
    }
    
    public TgraphHandler() {
    }
    
    // 从context中获取当前用户名
    private String getCurrentUsername(Context ctx) {
        Object userObj = ctx.attribute("user");
        if (userObj != null && userObj instanceof User) {
            return ((User) userObj).getUsername();
        }
        return null;
    }
    
    // 创建路径键，格式为 "username:databaseName"
    private String createPathKey(String username, String databaseName) {
        return username + ":" + databaseName;
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
            for (String key : props.stringPropertyNames()) {
                databasePaths.put(key, props.getProperty(key));
            }
            
            System.out.println("成功加载数据库路径信息: " + databasePaths.size() + " 个数据库");
        } catch (IOException e) {
            System.err.println("加载数据库路径信息时出错: " + e.getMessage());
        }
    }
    
    // 保存数据库路径信息
    private static void saveDatabasePaths() {
        Properties props = new Properties();
        for (Map.Entry<String, String> entry : databasePaths.entrySet()) {
            props.setProperty(entry.getKey(), entry.getValue());
        }
        
        try (FileOutputStream fos = new FileOutputStream(PATHS_FILE)) {
            props.store(fos, "数据库路径配置");
            System.out.println("成功保存数据库路径信息: " + databasePaths.size() + " 个数据库");
        } catch (IOException e) {
            System.err.println("保存数据库路径信息时出错: " + e.getMessage());
        }
    }
    
    // 创建数据库API
    public void createDatabase(Context ctx) {
        String databaseName = ctx.pathParam("databaseName");
        String username = getCurrentUsername(ctx);
        if (username == null) {
            ctx.status(401).json(createErrorResponse("未授权或会话已过期", "Neo.ClientError.Security.Unauthorized"));
            return;
        }
        
        try {
            Tgraph.graphDb = Tgraph.createDb(username, databaseName);
            
            // 记录数据库路径并持久化
            String pathKey = createPathKey(username, databaseName);
            String dbPath = Tgraph.TARGET_DIR + File.separator + username + File.separator + databaseName;
            databasePaths.put(pathKey, dbPath);
            saveDatabasePaths(); // 保存到文件
        } catch (Exception e) {
            e.printStackTrace();
            ctx.status(500).json(createErrorResponse("创建数据库失败: " + e.getMessage(), "Neo.DatabaseError.General.UnknownError"));
            return;
        }
        ctx.status(201);
    }

    // 启动数据库 - 确保也记录路径
    public void startDatabase(Context ctx) {
        String databaseName = ctx.pathParam("databaseName");
        String username = getCurrentUsername(ctx);
        if (username == null) {
            ctx.status(401).json(createErrorResponse("未授权或会话已过期", "Neo.ClientError.Security.Unauthorized"));
            return;
        }
        
        if (Tgraph.graphDb != null) {
            ctx.status(409).json(createErrorResponse("已有数据库在运行，请先关闭当前数据库", "Neo.ClientError.General.DatabaseError"));
            return;
        }
        
        try {
            Tgraph.graphDb = Tgraph.startDb(username, databaseName);
            
            // 如果数据库路径尚未记录，则记录并持久化
            String pathKey = createPathKey(username, databaseName);
            if (!databasePaths.containsKey(pathKey)) {
                String dbPath = Tgraph.TARGET_DIR + File.separator + username + File.separator + databaseName;
                databasePaths.put(pathKey, dbPath);
                saveDatabasePaths(); // 保存到文件
            }
            
            ctx.status(201);
        } catch (Exception e) {
            ctx.status(404).json(createErrorResponse("数据库不存在或无法访问: " + e.getMessage(), "Neo.ClientError.General.DatabaseNotFound"));
        }
    }

    // 删除数据库 - 同时从路径记录中移除
    public void deleteDatabase(Context ctx) {
        String databaseName = ctx.pathParam("databaseName");
        String username = getCurrentUsername(ctx);
        if (username == null) {
            ctx.status(401).json(createErrorResponse("未授权或会话已过期", "Neo.ClientError.Security.Unauthorized"));
            return;
        }
        
        boolean isDelete = Tgraph.deleteDb(username, databaseName);
        if (isDelete) {
            // 从路径记录中移除
            String pathKey = createPathKey(username, databaseName);
            databasePaths.remove(pathKey);
            saveDatabasePaths();
            
            ctx.status(200);
        } else {
            ctx.status(500).json(createErrorResponse("删除数据库失败", "Neo.DatabaseError.General.UnknownError"));
        }
    }
    
    // 辅助方法：创建错误响应
    private Map<String, Object> createErrorResponse(String message, String code) {
        Map<String, Object> errorResponse = new HashMap<>();
        List<Map<String, String>> errors = new ArrayList<>();
        Map<String, String> error = new HashMap<>();
        error.put("message", message);
        error.put("code", code);
        errors.add(error);
        errorResponse.put("errors", errors);
        return errorResponse;
    }
    
    // 关闭数据库
    public void shutdownDatabase(Context ctx) {
        if (Tgraph.graphDb != null) {
            Tgraph.shutDown(Tgraph.graphDb);
            Tgraph.graphDb = null;
            ctx.status(200);
        } else {
            ctx.status(404).json(createErrorResponse("没有正在运行的数据库", "Neo.ClientError.General.DatabaseNotFound"));
        }
    }
    
    // 备份数据库API
    public void backupDatabase(Context ctx) {
        String databaseName = ctx.pathParam("databaseName");
        String username = getCurrentUsername(ctx);
        if (username == null) {
            ctx.status(401).json(createErrorResponse("未授权或会话已过期", "Neo.ClientError.Security.Unauthorized"));
            return;
        }
        
        try {
            Tgraph.backupDatabase(username, databaseName);
            Map<String, String> response = new HashMap<>();
            response.put("message", "数据库备份成功");
            ctx.status(200).json(response);
        } catch (IllegalArgumentException e) {
            ctx.status(404).json(createErrorResponse(e.getMessage(), "Neo.ClientError.General.DatabaseNotFound"));
        } catch (IllegalStateException e) {
            ctx.status(409).json(createErrorResponse(e.getMessage(), "Neo.ClientError.General.DatabaseError"));
        } catch (IOException e) {
            ctx.status(500).json(createErrorResponse("备份数据库失败: " + e.getMessage(), "Neo.DatabaseError.General.UnknownError"));
        }
    }
    
    // 恢复数据库API
    public void restoreDatabase(Context ctx) {
        String backupFileName = ctx.pathParam("backupFileName");
        String username = getCurrentUsername(ctx);
        if (username == null) {
            ctx.status(401).json(createErrorResponse("未授权或会话已过期", "Neo.ClientError.Security.Unauthorized"));
            return;
        }
        
        try {
            Tgraph.restoreDatabase(username, backupFileName);
            
            // 从备份文件名中解析数据库名
            String fileNameWithoutExt = backupFileName.substring(0, backupFileName.lastIndexOf('.'));
            String[] parts = fileNameWithoutExt.split("_");
            String dbName = parts[1];
            for (int i = 2; i < parts.length - 2; i++) {
                dbName += "_" + parts[i];
            }
            
            // 更新路径记录
            String pathKey = createPathKey(username, dbName);
            String dbPath = Tgraph.TARGET_DIR + File.separator + username + File.separator + dbName;
            databasePaths.put(pathKey, dbPath);
            saveDatabasePaths();
            
            Map<String, String> response = new HashMap<>();
            response.put("message", "数据库恢复成功");
            ctx.status(200).json(response);
        } catch (IllegalArgumentException e) {
            ctx.status(400).json(createErrorResponse(e.getMessage(), "Neo.ClientError.General.InvalidArguments"));
        } catch (IllegalStateException e) {
            ctx.status(409).json(createErrorResponse(e.getMessage(), "Neo.ClientError.General.DatabaseError"));
        } catch (IOException e) {
            ctx.status(500).json(createErrorResponse("恢复数据库失败: " + e.getMessage(), "Neo.DatabaseError.General.UnknownError"));
        }
    }
    
    // 获取数据库路径API
    public void getDatabasePath(Context ctx) {
        String databaseName = ctx.pathParam("databaseName");
        String username = getCurrentUsername(ctx);
        if (username == null) {
            ctx.status(401).json(createErrorResponse("未授权或会话已过期", "Neo.ClientError.Security.Unauthorized"));
            return;
        }
        
        String pathKey = createPathKey(username, databaseName);
        String path = databasePaths.get(pathKey);
        
        if (path != null) {
            Map<String, Object> response = new HashMap<>();
            response.put("database", databaseName);
            response.put("path", path);
            ctx.status(200).json(response);
        } else {
            ctx.status(404).json(createErrorResponse("数据库 '" + databaseName + "' 不存在或未记录路径", "Neo.ClientError.General.DatabaseNotFound"));
        }
    }                    
    
    // 获取数据库状态
    public void getDatabaseStatus(Context ctx) {
        String databaseName = ctx.pathParam("databaseName");
        String username = getCurrentUsername(ctx);
        if (username == null) {
            ctx.status(401).json(createErrorResponse("未授权或会话已过期", "Neo.ClientError.Security.Unauthorized"));
            return;
        }
        
        String dbDir = Tgraph.TARGET_DIR + File.separator + username + File.separator + databaseName;
        Map<String, Object> response = new HashMap<>();
        
        try {
            // 尝试打开数据库
            GraphDatabaseService graphDb = new GraphDatabaseFactory()
                .newEmbeddedDatabaseBuilder(new File(dbDir))
                .newGraphDatabase();
            // 能成功打开说明数据库没有被其他进程使用（关闭状态）
            graphDb.shutdown();
            
            // 返回关闭状态
            response.put("status", "stopped");
            ctx.status(200).json(response);
        } catch (Exception e) {
            // 捕获异常意味着数据库可能正在被其他进程使用（启动状态）或不存在
            if (new File(dbDir).exists()) {
                response.put("status", "running");
                ctx.status(200).json(response);
            } else {
                ctx.status(404).json(createErrorResponse("数据库 '" + databaseName + "' 不存在", "Neo.ClientError.General.DatabaseNotFound"));
            }
        }
    }
}
