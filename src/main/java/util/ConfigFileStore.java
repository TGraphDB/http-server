package util;

import java.io.*;
import java.util.Properties;

public class ConfigFileStore {
    private static final String CONFIG_DIR = "config";
    private static final String CONFIG_FILE = CONFIG_DIR + File.separator + "tgraph_users.properties";
    
    static {
        // 确保配置目录存在
        new File(CONFIG_DIR).mkdirs();
    }
    
    public static void saveUserCredentials(String username, String passwordHash, boolean passwordChangeRequired) {
        Properties props = loadProperties();
        props.setProperty(username + ".password", passwordHash);
        props.setProperty(username + ".passwordChangeRequired", String.valueOf(passwordChangeRequired)); // passwordChangeRequired是一个bool
        saveProperties(props);
    }
    
    public static Properties loadUserCredentials() {
        return loadProperties();
    }
    
    private static Properties loadProperties() {
        Properties props = new Properties();
        File file = new File(CONFIG_FILE);
        if (file.exists()) {
            try (FileInputStream fis = new FileInputStream(file)) {
                props.load(fis);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return props;
    }
    
    private static void saveProperties(Properties props) {
        try (FileOutputStream fos = new FileOutputStream(CONFIG_FILE)) {
            props.store(fos, "TGraph User Credentials");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
} 