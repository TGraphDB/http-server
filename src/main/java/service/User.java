package service;

// auth.enabled做持久化存储需要记录是哪个user或者是哪个database

public class User { // 每个user的数据库放在target/{username}/{databaseName}下
    private String username;
    private String passwordHash;
    private boolean passwordChangeRequired;
    
    public User(String username, String passwordHash, boolean passwordChangeRequired) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.passwordChangeRequired = passwordChangeRequired;
    }
    
    // Getters and setters
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public boolean isPasswordChangeRequired() { return passwordChangeRequired; }
    public void setPasswordChangeRequired(boolean required) { this.passwordChangeRequired = required; }
} 