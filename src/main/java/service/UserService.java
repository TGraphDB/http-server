package service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Properties;
import util.PasswordUtil;
import util.ConfigFileStore;

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
        // 检查用户名是否存在
        if (users.containsKey(username)) {
            return "用户名已存在";
        }
        
        // 检查用户名和密码是否有效
        if (!isValidUsername(username)) {
            return "用户名无效，必须为5-20个字符的字母数字组合";
        }
        
        if (!isValidPassword(password)) {
            return "密码无效，必须至少包含8个字符";
        }
        
        // 对密码进行哈希处理
        String passwordHash = PasswordUtil.hashPassword(password);
        
        // 创建用户
        users.put(username, new User(username, passwordHash, false));
        
        // 保存用户信息
        saveUsers();
        
        return null; // 返回null表示成功
    }
    
    // 验证用户名格式
    private boolean isValidUsername(String username) {
        return username != null && username.matches("^[a-zA-Z0-9]{5,20}$");
    }
    
    // 验证密码格式
    private boolean isValidPassword(String password) {
        return password != null && password.length() >= 8;
    }
    
    private void saveUsers() {
        // Implementation of saveUsers method
    }
} 