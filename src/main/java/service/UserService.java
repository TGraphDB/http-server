package service;

import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Properties;
import util.PasswordUtil;
import util.ConfigFileStore;
import util.UserLogger;
import java.io.File;

public class UserService {
    private static final Map<String, User> users = new ConcurrentHashMap<>();
    
    public UserService() { // 构造函数
        // 从配置文件加载用户
        loadUsers();
        
        // 如果没有用户，创建默认用户
        if (users.isEmpty()) {
            String defaultHash = PasswordUtil.hashPassword("tgraph");
            createUser("tgraph", defaultHash, true);
        }
    }
    
    private void loadUsers() {
        Properties props = ConfigFileStore.loadUserCredentials();
        props.stringPropertyNames().stream()
            .filter(key -> key.endsWith(".password"))
            .map(key -> key.substring(0, key.length() - 9))
            .forEach(username -> {
                String passwordHash = props.getProperty(username + ".password");
                boolean passwordChangeRequired = Boolean.parseBoolean(
                    props.getProperty(username + ".passwordChangeRequired", "false")
                );
                users.put(username, new User(username, passwordHash, passwordChangeRequired));
            });
    }
    
    private void createUser(String username, String passwordHash, boolean passwordChangeRequired) {
        users.put(username, new User(username, passwordHash, passwordChangeRequired));
        ConfigFileStore.saveUserCredentials(username, passwordHash, passwordChangeRequired);
    }
    
    public User getUserStatus(String username) {
        return users.get(username);
    }
    
    public boolean changePassword(String username, String currentPassword, String newPassword) {
        User user = users.get(username);
        if (user == null) return false;
        
        if (!PasswordUtil.checkPassword(currentPassword, user.getPasswordHash())) {
            return false;
        }
        
        if (PasswordUtil.checkPassword(newPassword, user.getPasswordHash())) {
            return false;
        }
        
        String newHash = PasswordUtil.hashPassword(newPassword);
        user.setPasswordHash(newHash);
        user.setPasswordChangeRequired(false);
        
        // 保存到配置文件
        ConfigFileStore.saveUserCredentials(username, newHash, false);
        return true;
    }
    
    /**
     * 用户自助注册（公开API，无需管理员权限）
     * @param username 用户名
     * @param password 密码（明文）
     * @return 注册结果信息：成功返回null，失败返回错误消息
     */
    public String registerUser(String username, String password) {
        // 检查用户名格式
        if (username == null || username.trim().isEmpty() || username.length() < 3) {
            return "用户名不能为空且长度不能少于3个字符";
        }
        
        // 检查用户名是否包含特殊字符（只允许字母、数字、下划线）
        if (!username.matches("^[a-zA-Z0-9_]+$")) {
            return "用户名只能包含字母、数字和下划线";
        }
        
        // 保留关键词检查，防止用户注册admin、system等敏感用户名
        if (username.equalsIgnoreCase("admin") || 
            username.equalsIgnoreCase("administrator") || 
            username.equalsIgnoreCase("system") || 
            username.equalsIgnoreCase("root") ||
            username.equals("tgraph")) {
            return "用户名 '" + username + "' 是保留名称，不能注册";
        }
        
        // 检查密码强度
        if (password == null || password.length() < 6) {
            return "密码不能为空且长度不能少于6个字符";
        }
        
        // 检查用户是否已存在
        synchronized (users) {
            if (users.containsKey(username)) {
                return "用户名 '" + username + "' 已存在";
            }
            
            // 创建新用户 (默认需要修改密码为false，因为是用户自己设置的密码)
            String passwordHash = PasswordUtil.hashPassword(password);
            createUser(username, passwordHash, false);
            
            // 预创建用户日志目录
            ensureUserLogDirectory(username);
            
            System.out.println("新用户注册成功: " + username);
            return null; // 成功返回null
        }
    }
    
    /**
     * 确保用户日志目录存在
     * @param username 用户名
     */
    private void ensureUserLogDirectory(String username) {
        try {
            // 获取用户日志目录路径
            // 注意：这里与UserLogger中的路径保持一致
            File userLogDir = new File("target/logs", username);
            if (!userLogDir.exists()) {
                userLogDir.mkdirs();
                System.out.println("为用户 '" + username + "' 创建日志目录: " + userLogDir.getAbsolutePath());
            }
        } catch (Exception e) {
            System.err.println("为用户 '" + username + "' 创建日志目录时出错: " + e.getMessage());
        }
    }
} 