package config;

import java.io.*;
import java.util.Properties;

public class NodeIdManager {
    private static final String CONFIG_FILE_PATH = "config/id.properties";
    private static int nodeid;

    // 加载 nodeid
    public static void loadNodeId() {
        Properties properties = new Properties();
        try (InputStream input = new FileInputStream(CONFIG_FILE_PATH)) {
            properties.load(input);
            nodeid = Integer.parseInt(properties.getProperty("nodeid", "0"));
        } catch (IOException e) {
            e.printStackTrace();
            nodeid = 0; // 默认值
        }
    }

    // 保存 nodeid
    public static void saveNodeId(int currentNodeId) {
        Properties properties = new Properties();
        properties.setProperty("nodeid", String.valueOf(currentNodeId));
        try (OutputStream output = new FileOutputStream(CONFIG_FILE_PATH)) {
            properties.store(output, null);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // 获取当前 nodeid
    public static int getNodeId() {
        return nodeid;
    }

    // 增加 nodeid
    public static void incrementNodeId() {
        nodeid++;
    }
} 