package handlers;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.javalin.http.Context;
import service.User;

// 有问题

/**
 * 用户日志处理器，提供用户查看自己日志的功能
 */
public class UserLogHandler {
    private static final String LOGS_DIR = "target/logs";
    private static final Pattern TIME_PATTERN = Pattern.compile("^(\\d{2}:\\d{2}:\\d{2}\\.\\d{3})");
    private static final Pattern LOGIN_PATTERN = Pattern.compile("Request: POST \\[/user/login\\]");
    private static final SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("HH:mm:ss.SSS");
    private static final SimpleDateFormat DISPLAY_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    
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
        
        // 获取当前登录用户的用户名
        String username = ((User)userObj).getUsername();
        
        try {
            File logFile = new File(LOGS_DIR, username  + ".log");
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
    
    /**
     * 获取用户列表和他们的登录/请求时间
     */
    // 都可以查看
    public void getUsersList(Context ctx) {
        Object userObj = ctx.attribute("user");
        if (userObj == null || !(userObj instanceof User)) {
            ctx.status(401).json(createErrorResponse("未授权或会话已过期", "Error.Security.Unauthorized"));
            return;
        }
        
        // String username = ((User)userObj).getUsername();
        
        File logsDir = new File(LOGS_DIR);
        if (!logsDir.exists() || !logsDir.isDirectory()) {
            ctx.status(404).json(createErrorResponse("日志目录不存在", "Error.Resource.NotFound"));
            return;
        }
        
        List<Map<String, Object>> usersList = new ArrayList<>();
        File[] logFiles = logsDir.listFiles((dir, name) -> name.endsWith(".log"));
        
        if (logFiles != null) {
            for (File logFile : logFiles) {
                String logUsername = logFile.getName().replace(".log", "");
                
                // 获取文件最后修改时间，用于补充日期信息
                long lastModified = logFile.lastModified();
                Date fileDate = new Date(lastModified);
                
                // 默认值
                Date lastLoginTime = null;
                Date lastRequestTime = null;
                
                try {
                    // 尝试从文件末尾读取最近的几条记录
                    List<String> lastLines = readLastLines(logFile, 1000); // 读取最后1000行，足够覆盖大多数情况
                    
                    // 最后一次请求时间就是日志的最后一条时间记录
                    for (int i = lastLines.size() - 1; i >= 0 ; i--) {
                        String line = lastLines.get(i);
                        Matcher matcher = TIME_PATTERN.matcher(line);
                        if (matcher.find()) {
                            try {
                                // 解析时间并补充日期信息
                                lastRequestTime = parseTimeWithDate(matcher.group(1), fileDate);
                                break;
                            } catch (ParseException e) {
                                // 忽略解析错误，继续查找
                            }
                        }
                    }
                    
                    // 找出最后一次登录时间
                    for (int i = lastLines.size() - 1; i >= 0 ; i--) {
                        String line = lastLines.get(i);
                        if (LOGIN_PATTERN.matcher(line).find() && i > 0) {
                            // 找到登录请求，获取其时间戳
                            String previousLine = lastLines.get(i - 1);
                            Matcher matcher = TIME_PATTERN.matcher(previousLine);
                            if (matcher.find()) {
                                try {
                                    // 解析时间并补充日期信息
                                    lastLoginTime = parseTimeWithDate(matcher.group(1), fileDate);
                                    break;
                                } catch (ParseException e) {
                                    // 忽略解析错误，继续查找
                                }
                            }
                        }
                    }
                } catch (IOException e) {
                    // 忽略单个文件的错误，继续处理其他文件
                    System.err.println("读取文件失败: " + logFile.getName() + " - " + e.getMessage());
                }
                
                // 添加用户信息到列表
                Map<String, Object> userInfo = new HashMap<>();
                userInfo.put("username", logUsername);
                userInfo.put("lastLoginTime", lastLoginTime != null ? DISPLAY_FORMAT.format(lastLoginTime) : "未登录");
                userInfo.put("lastRequestTime", lastRequestTime != null ? DISPLAY_FORMAT.format(lastRequestTime) : "无请求");
                usersList.add(userInfo);
            }
        }
        
        Map<String, Object> response = new HashMap<>();
        response.put("users", usersList);
        ctx.status(200).json(response);
    }
    
    /**
     * 解析时间并补充日期信息
     */
    private Date parseTimeWithDate(String timeStr, Date fileDate) throws ParseException {
        // 解析时间
        Date timeOnly = TIME_FORMAT.parse(timeStr);
        
        // 补充日期信息
        Calendar fileCal = Calendar.getInstance();
        fileCal.setTime(fileDate);
        
        Calendar timeCal = Calendar.getInstance();
        timeCal.setTime(timeOnly);
        
        timeCal.set(Calendar.YEAR, fileCal.get(Calendar.YEAR));
        timeCal.set(Calendar.MONTH, fileCal.get(Calendar.MONTH));
        timeCal.set(Calendar.DAY_OF_MONTH, fileCal.get(Calendar.DAY_OF_MONTH));
        
        return timeCal.getTime();
    }
    
    /**
     * 从文件末尾读取指定行数
     */
    private List<String> readLastLines(File file, int lines) throws IOException {
        LinkedList<String> result = new LinkedList<>();
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            long fileLength = raf.length();
            if (fileLength == 0) {
                return result;
            }
            
            // 从文件末尾开始
            long pointer = fileLength - 1;
            int linesRead = 0;
            StringBuilder sb = new StringBuilder();
            
            // 从后向前读取文件
            while (pointer >= 0 && linesRead < lines) {
                raf.seek(pointer);
                char c = (char) raf.read();
                
                // 找到行尾
                if (c == '\n' && sb.length() > 0) {
                    // 反转字符串并添加到结果中
                    result.addFirst(sb.reverse().toString());
                    sb = new StringBuilder();
                    linesRead++;
                } else {
                    sb.append(c);
                }
                
                pointer--;
            }
            
            // 处理最后一行
            if (sb.length() > 0) {
                result.addFirst(sb.reverse().toString());
            }
        }
        return result;
    }
} 