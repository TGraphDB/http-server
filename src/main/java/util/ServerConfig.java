package util;

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
     * 加载并应用配置
     */
    public static void loadAndApplyConfig(String path) {
        // 加载配置
        try (InputStream input = new FileInputStream(path)) {
            Properties props = new Properties();
            props.load(input);
            
            // 转换并存储属性值
            for (String key : props.stringPropertyNames()) {
                String value = props.getProperty(key);
                
                // 转换值类型并存储
                if (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("false")) {
                    config.put(key, Boolean.parseBoolean(value));
                } else {
                    try {
                        config.put(key, Integer.parseInt(value));
                    } catch (NumberFormatException e) {
                        config.put(key, value);
                    }
                }
                
                // 直接应用到系统属性
                System.setProperty(key, value);
            }
            System.out.println("已加载并应用配置文件: " + path);
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
}