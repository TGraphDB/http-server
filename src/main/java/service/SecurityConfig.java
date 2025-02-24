package service;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Properties;

public class SecurityConfig {
    private static boolean authEnabled = true;

    private static final String CONFIG_DIR = "config";
    private static final String CONFIG_FILE = CONFIG_DIR + File.separator + "security.properties";
    
    static {
        loadConfig();
    }
    
    private static void loadConfig() {
        Properties props = new Properties();
        try (FileInputStream in = new FileInputStream(CONFIG_FILE)) {
            props.load(in);
            authEnabled = Boolean.parseBoolean(props.getProperty("auth.enabled", "true"));
        } catch (Exception e) {
            // 如果文件不存在,使用默认值并创建文件
            saveConfig();
        }
    }
    
    private static void saveConfig() {
        Properties props = new Properties();
        props.setProperty("auth.enabled", String.valueOf(authEnabled));
        try (FileOutputStream out = new FileOutputStream(CONFIG_FILE)) {
            props.store(out, "Security Configuration");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    public static boolean isAuthEnabled() {
        return authEnabled;
    }
    
    public static void setAuthEnabled(boolean enabled) {
        authEnabled = enabled;
        saveConfig();  // 保存更改到文件
    }
}