package service;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 会话管理器，管理用户会话
 */
public class SessionManager {
    // 会话超时时间（30分钟）
    private static final long SESSION_TIMEOUT_MS = 30 * 60 * 1000;
    // 最大会话存活时间（7天），适用于"记住我"功能
    private static final long MAX_SESSION_LIFETIME_MS = 7 * 24 * 60 * 60 * 1000;
    
    // 会话存储
    private static final Map<String, SessionInfo> sessions = new ConcurrentHashMap<>();
    // 安全随机数生成器
    private static final SecureRandom secureRandom = new SecureRandom();
    // 调度器，用于清理过期会话
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    
    static {
        // 启动定期清理过期会话的任务（每10分钟执行一次）
        scheduler.scheduleAtFixedRate(() -> {
            long now = System.currentTimeMillis();
            sessions.entrySet().removeIf(entry -> 
                entry.getValue().expiryTime < now);
        }, 10, 10, TimeUnit.MINUTES);
        
        // 添加JVM关闭钩子，确保调度器正确关闭
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
            }
        }));
    }
    
    /**
     * 创建新会话
     * @param username 用户名
     * @param rememberMe 是否启用"记住我"功能
     * @return 会话ID
     */
    public static String createSession(String username, boolean rememberMe) {
        // 生成随机会话ID
        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        String sessionId = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        
        // 计算过期时间
        long expiryTime = System.currentTimeMillis() + 
            (rememberMe ? MAX_SESSION_LIFETIME_MS : SESSION_TIMEOUT_MS);
        
        // 保存会话信息
        sessions.put(sessionId, new SessionInfo(username, expiryTime, rememberMe));
        
        return sessionId;
    }
    
    /**
     * 验证会话是否有效
     * @param sessionId 会话ID
     * @return 如果会话有效，返回用户名；否则返回null
     */
    public static String validateSession(String sessionId) {
        if (sessionId == null) {
            return null;
        }
        
        SessionInfo sessionInfo = sessions.get(sessionId);
        if (sessionInfo == null || sessionInfo.expiryTime < System.currentTimeMillis()) {
            // 会话不存在或已过期
            if (sessionInfo != null) {
                // 如果会话已过期，从会话存储中移除
                sessions.remove(sessionId);
            }
            return null;
        }
        
        // 如果不是"记住我"会话，更新过期时间（滑动会话）
        if (!sessionInfo.rememberMe) {
            sessionInfo.expiryTime = System.currentTimeMillis() + SESSION_TIMEOUT_MS;
        }
        
        return sessionInfo.username;
    }
    
    /**
     * 使会话无效（登出）
     * @param sessionId 会话ID
     */
    public static void invalidateSession(String sessionId) {
        if (sessionId != null) {
            sessions.remove(sessionId);
        }
    }
    
    /**
     * 会话信息类
     */
    private static class SessionInfo {
        final String username;
        long expiryTime;
        final boolean rememberMe;
        
        public SessionInfo(String username, long expiryTime, boolean rememberMe) {
            this.username = username;
            this.expiryTime = expiryTime;
            this.rememberMe = rememberMe;
        }
    }
} 