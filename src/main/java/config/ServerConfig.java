package config;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class ServerConfig {
    private static Map<String, Object> config = new HashMap<>();
    
    // 默认值
    static {
        config.put("org.neo4j.server.webserver.port", 7474);
        config.put("org.neo4j.server.webserver.address", "0.0.0.0");
        config.put("org.neo4j.server.webserver.maxthreads", 200);
        config.put("org.neo4j.server.transaction.timeout", 60);
        config.put("org.neo4j.server.http.log.enabled", true);
    }
    
    /**
     * 加载Properties配置文件
     */
    public static void loadPropertiesConfig(String path) {
        try (InputStream input = new FileInputStream(path)) {
            Properties props = new Properties();
            props.load(input);
            
            // 转换属性值为适当的类型
            for (String key : props.stringPropertyNames()) {
                String value = props.getProperty(key);
                
                // 尝试转换为数字或布尔值
                if (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("false")) {
                    config.put(key, Boolean.parseBoolean(value));
                } else {
                    try {
                        config.put(key, Integer.parseInt(value));
                    } catch (NumberFormatException e) {
                        config.put(key, value); // 保持为字符串
                    }
                }
            }
            System.out.println("已加载Properties配置文件: " + path);
        } catch (IOException e) {
            System.err.println("无法加载配置文件: " + path);
            e.printStackTrace();
        }
    }
    
    /**
     * 获取配置值
     */
    public static Object get(String key) {
        return config.getOrDefault(key, null);
    }
    
    /**
     * 获取整数配置值
     */
    public static int getInt(String key, int defaultValue) {
        Object value = config.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return defaultValue;
    }
    
    /**
     * 获取字符串配置值
     */
    public static String getString(String key, String defaultValue) {
        Object value = config.get(key);
        return value != null ? value.toString() : defaultValue;
    }
    
    /**
     * 获取布尔配置值
     */
    public static boolean getBoolean(String key, boolean defaultValue) {
        Object value = config.get(key);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return defaultValue;
    }
    
    /**
     * 应用配置到系统
     */
    public static void applyConfig() {
        // 可以在这里添加系统级别的配置应用
        System.setProperty("org.neo4j.server.transaction.timeout", 
                           getString("org.neo4j.server.transaction.timeout", "60"));
    }
}