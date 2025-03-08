package app;

import java.io.File;
import java.io.FilenameFilter;
import java.util.HashMap;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

/**
 * 数据库空间统计工具
 * 用于统计Neo4j数据库的存储空间使用情况
 */
public class DBSpace {
    
    private static final String DB_PATH = "target/neo4j-hello-db"; // 默认数据库路径
    private static final String TARGET_DIR = "target"; // 目标目录
    private static final Gson gson = new Gson();
    
    /**
     * 获取Neo4j数据库的空间使用统计
     * @return 包含数据和索引空间使用的JSON字符串
     */
    public static String getNeo4jSpaceStats() {
        return neo4jSize(new File(DB_PATH));
    }
    
    /**
     * 获取指定路径Neo4j数据库的空间使用统计
     * @param dbPath 数据库路径
     * @return 包含数据和索引空间使用的JSON字符串
     */
    public static String getNeo4jSpaceStats(String dbPath) {
        return neo4jSize(new File(dbPath));
    }
    
    /**
     * 获取数据库空间使用情况的API响应
     * @return 包含统计信息的Map
     */
    public static Map<String, Object> getSpaceStatsResponse() {
        String statsJson = getNeo4jSpaceStats();
        Map<String, Object> response = new HashMap<>();
        
        // 解析JSON字符串，提取数据
        try {
            JsonObject obj = new JsonObject();
            obj.addProperty("space_stats", statsJson);
            obj.addProperty("db_path", DB_PATH);
            response.put("space_statistics", gson.fromJson(obj.toString(), Map.class));
        } catch (Exception e) {
            response.put("error", "无法获取空间统计信息: " + e.getMessage());
        }
        
        return response;
    }

    /**
     * 获取用户日志文件大小的API响应
     * @param username 用户名
     * @return 包含统计信息的Map
     */
    public static Map<String, Object> getUserLogsSizeResponse(String username) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            // 获取日志目录
            File logsDir = new File(TARGET_DIR + File.separator + "logs" + File.separator + username);
            
            // 计算日志信息
            JsonObject logStatsJson = calculateLogStats(logsDir, username);
            
            // 将JsonObject转换为Map
            Map<String, Object> logsStats = gson.fromJson(logStatsJson.toString(), Map.class);
            
            // 创建返回结构
            Map<String, Object> statistics = new HashMap<>();
            statistics.put("logs_stats", logsStats);
            statistics.put("logs_path", TARGET_DIR + "/logs/" + username);
            
            response.put("logs_statistics", statistics);
        } catch (Exception e) {
            response.put("error", "无法获取日志统计信息: " + e.getMessage());
        }
        
        return response;
    }
    
    /**
     * 格式化字节大小为可读形式
     * @param size 字节大小
     * @return 格式化后的大小字符串
     */
    private static String formatSize(long size) {
        if (size < 1024) return size + " B";
        int exp = (int) (Math.log(size) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp-1) + "";
        return String.format("%.2f %sB", size / Math.pow(1024, exp), pre);
    }

    private static long size(final File file, FilenameFilter filter, int level) {
        if (file.isFile()) return file.length();
        final File[] children = file.listFiles(filter);
        long total = 0;
        if (children != null)
            for (final File child : children){
                StringBuilder sb = new StringBuilder();
                for(int i=0;i<level;i++) sb.append("|--");
                sb.append(child.getName()).append('\t').append(child.isFile()?child.length()+"":"");
                System.out.println(sb);
                total += size(child, filter, level+1);
            }
        return total;
    }

    private static long size(final File file, FilenameFilter filter) {
        System.out.println("------------------"+ file.getAbsolutePath()+"------------------");
        return size(file, filter, 0);
    }

    private static final FilenameFilter noFilter = (d, name) -> true;
    
    /**
     * 计算Neo4j数据库大小
     * @param dir 数据库目录
     * @return 包含数据和索引空间使用的JSON字符串
     */
    private static String neo4jSize(File dir){
        if (!dir.exists()) {
            JsonObject errorObj = new JsonObject();
            errorObj.addProperty("error", "数据库目录不存在");
            return gson.toJson(errorObj);
        }
        
        JsonObject obj = new JsonObject();
        java.util.function.Function<String, Boolean> isIndex = (name) -> 
            name.equalsIgnoreCase("schema") || name.startsWith("index");
            
        long dbSize = size(dir, (dir1, name) -> !isIndex.apply(name));
        long indexSize = 0;
        
        File schemaDir = new File(dir, "schema");
        File indexDir = new File(dir, "index");
        
        if (schemaDir.exists()) {
            indexSize += size(schemaDir, noFilter);
        }
        
        if (indexDir.exists()) {
            indexSize += size(indexDir, noFilter);
        }
        
        obj.addProperty("data", dbSize);
        obj.addProperty("index", indexSize);
        obj.addProperty("total", dbSize + indexSize);
        return gson.toJson(obj);
    }

    /**
     * 计算日志目录大小
     * @param dir 日志目录
     * @param username 用户名
     * @return 包含日志文件大小的JsonObject
     */
    private static JsonObject calculateLogStats(File dir, String username) {
        JsonObject obj = new JsonObject();
        
        if (!dir.exists()) {
            obj.addProperty("exists", false);
            obj.addProperty("total", 0);
            obj.addProperty("message", "日志目录不存在: " + dir.getAbsolutePath());
            return obj;
        }
        
        obj.addProperty("exists", true);
        
        // 计算日志目录总大小
        long totalSize = size(dir, noFilter);
        
        // 获取文件数量
        File[] logFiles = dir.listFiles((d, name) -> name.endsWith(".log"));
        int fileCount = logFiles != null ? logFiles.length : 0;
        
        obj.addProperty("total", totalSize);
        obj.addProperty("totalFormatted", formatSize(totalSize));
        obj.addProperty("fileCount", fileCount);
        obj.addProperty("username", username);
        
        return obj;
    }
}