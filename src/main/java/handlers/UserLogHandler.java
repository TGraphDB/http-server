package handlers;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.javalin.http.Context;
import service.User;

/**
 * 用户日志处理器，提供用户查看自己日志的功能
 */
public class UserLogHandler {
    private static final String LOGS_DIR = "target/logs";
    
    /**
     * 获取用户日志内容
     */
    public void getUserLog(Context ctx) {
        // 从ctx获取当前用户
        Object userObj = ctx.attribute("user");
        if (userObj == null || !(userObj instanceof User)) {
            ctx.status(401).json(createErrorResponse("未授权或会话已过期", "Error.Security.Unauthorized"));
            return;
        }
        
        User user = (User) userObj;
        String username = user.getUsername();
        
        try {
            // 日志文件路径
            File logFile = new File(new File(LOGS_DIR, username), "console.log");
            if (!logFile.exists()) {
                ctx.status(404).json(createErrorResponse("日志文件不存在", "Error.Resource.NotFound"));
                return;
            }
            
            // 获取分页参数
            String tailParam = ctx.queryParam("tail");
            String linesParam = ctx.queryParam("lines");
            
            // 默认读取最后100行
            boolean tail = tailParam == null || Boolean.parseBoolean(tailParam);
            int lines = linesParam != null ? Integer.parseInt(linesParam) : 100;
            
            List<String> logLines;
            if (tail) {
                // 读取文件最后N行
                logLines = readLastNLines(logFile.toPath(), lines);
            } else {
                // 从头开始读取N行
                try (Stream<String> stream = Files.lines(Paths.get(logFile.getAbsolutePath()))) {
                    logLines = stream.limit(lines).collect(Collectors.toList());
                }
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("username", username);
            response.put("lines", logLines);
            ctx.status(200).json(response);
        } catch (IOException e) {
            ctx.status(500).json(createErrorResponse("读取日志文件失败: " + e.getMessage(), "Error.System.IOError"));
        } catch (NumberFormatException e) {
            ctx.status(400).json(createErrorResponse("无效的参数", "Error.Request.InvalidParameter"));
        }
    }
    
    /**
     * 读取文件最后N行
     */
    private List<String> readLastNLines(java.nio.file.Path filePath, int n) throws IOException {
        List<String> result = new ArrayList<>();
        
        // 读取所有行
        List<String> allLines = Files.readAllLines(filePath);
        if (allLines.isEmpty()) {
            return result;
        }
        
        // 计算起始索引
        int startIndex = Math.max(0, allLines.size() - n);
        
        // 返回最后n行
        return allLines.subList(startIndex, allLines.size());
    }
    
    /**
     * 创建错误响应
     */
    private Map<String, Object> createErrorResponse(String message, String code) {
        Map<String, Object> errorResponse = new HashMap<>();
        Map<String, String> error = new HashMap<>();
        error.put("message", message);
        error.put("code", code);
        errorResponse.put("error", error);
        return errorResponse;
    }
} 