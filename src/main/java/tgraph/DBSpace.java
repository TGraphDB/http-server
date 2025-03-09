package tgraph;

import java.io.File;
import java.io.FilenameFilter;
import java.util.HashMap;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

/**
 * 数据库空间统计工具
 * 用于统计TGraph数据库的存储空间使用情况
 */
public class DBSpace {
    
    private static final String TARGET_DIR = "target"; // 目标目录
    private static final Gson gson = new Gson();
    
    /**
     * 获取数据库空间使用情况的API响应
     * @param username 用户名
     * @param dbName 数据库名
     * @return 包含统计信息的Map
     */
    public static Map<String, Object> getSpaceStatsResponse(String username, String dbName) {
        String dbPath = TARGET_DIR + File.separator + username + File.separator + dbName;
        String statsJson = tgraphSize(new File(dbPath));
        Map<String, Object> response = new HashMap<>();
        
        try {
            // 解析为Map并添加日志文件大小
            Map<String, Object> statsMap = gson.fromJson(statsJson, new TypeToken<Map<String, Object>>(){}.getType());
            
            // 获取日志文件大小
            File logFile = new File(dbPath, "messages.log");
            long logSize = logFile.exists() ? logFile.length() : 0;
            File httpLogFile = new File(TARGET_DIR, "logs" + File.separator + username +  ".log");
            long httpLogSize = httpLogFile.exists() ? httpLogFile.length() : 0;

            // 为统计结果添加单位
            Map<String, Object> formattedStatsMap = new HashMap<>();
            for (Map.Entry<String, Object> entry : statsMap.entrySet()) {
                if (entry.getValue() instanceof Number) {
                    double size = ((Number) entry.getValue()).doubleValue();
                    formattedStatsMap.put(entry.getKey(), formatFileSize(size));
                } else {
                    formattedStatsMap.put(entry.getKey(), entry.getValue());
                }
            }
            
            // 添加日志文件大小（带单位）
            formattedStatsMap.put("tgrapg_log", formatFileSize(logSize));
            formattedStatsMap.put("http_log", formatFileSize(httpLogSize));
            
            response.put("space_statistics", formattedStatsMap);
            response.put("db_path", dbPath);
        } catch (Exception e) {
            response.put("error", "无法获取空间统计信息: " + e.getMessage());
        }
        
        return response;
    }
    
    /**
     * 格式化文件大小为人类可读格式
     * @param size 文件大小（字节）
     * @return 格式化后的字符串
     */
    private static String formatFileSize(double size) {
        final String[] units = {"B", "KB", "MB", "GB", "TB"};
        int unitIndex = 0;
        
        while (size >= 1024 && unitIndex < units.length - 1) {
            size /= 1024;
            unitIndex++;
        }
        
        // 保留两位小数
        return String.format("%.2f %s", size, units[unitIndex]);
    }
    
    /**
     * 计算目录大小
     * @param file 目录或文件
     * @param filter 文件过滤器
     * @param level 当前递归级别
     * @return 总大小（字节）
     */
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

    /**
     * 计算目录大小
     * @param file 目录或文件
     * @param filter 文件过滤器
     * @return 总大小（字节）
     */
    private static long size(final File file, FilenameFilter filter) {
        System.out.println("------------------"+ file.getAbsolutePath()+"------------------");
        return size(file, filter, 0);
    }

    /**
     * 接受所有文件的过滤器
     */
    private static final FilenameFilter noFilter = (d, name) -> true;
    
    /**
     * 计算TGraph数据库大小统计
     * @param path 数据库路径
     * @return 包含各部分大小信息的JSON字符串
     */
    private static String tgraphSize(File path) {
        long neoData = size(path, (d, name) -> !name.startsWith("index") && !name.equals("schema") && !name.startsWith("temporal"));
        long neoIndex = size(new File(path,"schema"), noFilter)+size(new File(path, "index"), noFilter);
        long tpNodeData = size(new File(path,"temporal.node.properties"), (d, name) -> !name.equals("index"));
        long tpNodeIndex = size(new File(path, "temporal.node.properties/index"), noFilter);
        long tpRelData = size(new File(path,"temporal.relationship.properties"), (d, name) -> !name.equals("index"));
        long tpRelIndex = size(new File(path, "temporal.relationship.properties/index"), noFilter);
        JsonObject obj = new JsonObject();
        obj.addProperty("neo_data", neoData);
        obj.addProperty("neo_index", neoIndex);
        obj.addProperty("tp_node_data", tpNodeData);
        obj.addProperty("tp_node_index", tpNodeIndex);
        obj.addProperty("tp_rel_data", tpRelData);
        obj.addProperty("tp_rel_index", tpRelIndex);
        return gson.toJson(obj);
    }
}