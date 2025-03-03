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
} 