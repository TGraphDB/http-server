package util;

import org.mindrot.jbcrypt.BCrypt;

public class PasswordUtil {
    public static String hashPassword(String plainTextPassword) { // 对密码进行哈希加密
        return BCrypt.hashpw(plainTextPassword, BCrypt.gensalt(12));
    }
    
    public static boolean checkPassword(String plainTextPassword, String hashedPassword) { // 检查密码是否匹配
        return BCrypt.checkpw(plainTextPassword, hashedPassword);
    }
} 