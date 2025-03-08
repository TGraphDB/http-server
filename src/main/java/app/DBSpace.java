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
}