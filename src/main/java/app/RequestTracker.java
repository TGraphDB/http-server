package app;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class RequestTracker {
    // 使用 ConcurrentHashMap 存储请求信息，以支持并发访问
    private static final Map<String, RequestInfo> activeRequests = new ConcurrentHashMap<>();

    // 记录请求开始
    public static void startRequest(String requestId, String path, String method) {
        activeRequests.put(requestId, new RequestInfo(requestId, path, method, LocalDateTime.now()));
    }

    // 记录请求结束
    public static void endRequest(String requestId) {
        activeRequests.remove(requestId);
    }

    // 获取当前活跃请求列表
    public static Map<String, RequestInfo> getActiveRequests() {
        return Collections.unmodifiableMap(activeRequests);
    }

    // 请求信息类
    public static class RequestInfo {
        private final String requestId;
        private final String path;
        private final String method;
        private final LocalDateTime startTime;

        public RequestInfo(String requestId, String path, String method, LocalDateTime startTime) {
            this.requestId = requestId;
            this.path = path;
            this.method = method;
            this.startTime = startTime;
        }

        // Getters
        public String getRequestId() { return requestId; }
        public String getPath() { return path; }
        public String getMethod() { return method; }
        public LocalDateTime getStartTime() { return startTime; }
    }
} 