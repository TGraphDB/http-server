package handlers;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.javalin.http.Context;
import service.User;

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
            // 检查日志目录是否存在
            File logDir = new File(LOGS_DIR);
            if (!logDir.exists()) {
                logDir.mkdirs();
                ctx.status(404).json(createErrorResponse("日志目录不存在，已创建", "Error.Resource.NotFound"));
                return;
            }
            
            // 获取日志文件
            File logFile = new File(LOGS_DIR, username + ".log");
            if (!logFile.exists()) {
                ctx.status(404).json(createErrorResponse("日志文件不存在: " + logFile.getAbsolutePath(), "Error.Resource.NotFound"));
                return;
            }
            
            // 检查文件大小
            long fileSize = logFile.length();
            if (fileSize == 0) {
                Map<String, Object> emptyResponse = new HashMap<>();
                emptyResponse.put("username", username);
                emptyResponse.put("lines", new ArrayList<>());
                emptyResponse.put("message", "日志文件为空");
                ctx.status(200).json(emptyResponse);
                return;
            }
            
            // 获取分页参数
            String linesParam = ctx.queryParam("lines");
            int lines = linesParam != null ? Integer.parseInt(linesParam) : 100;
            
            List<String> logLines;
            try {
                // 使用更安全的方式读取文件
                logLines = safeReadLines(logFile, lines);
            } catch (Exception e) {
                // 如果安全读取失败，尝试使用备用方法
                try {
                    logLines = fallbackReadLines(logFile);
                } catch (Exception ex) {
                    ctx.status(500).json(createErrorResponse(
                        "读取日志文件失败: " + e.getMessage() + ", 备用方法也失败: " + ex.getMessage(), 
                        "Error.System.IOError"));
                    return;
                }
            }
            
            // 处理尾部读取（默认）
            String tailParam = ctx.queryParam("tail");
            boolean tail = tailParam == null || Boolean.parseBoolean(tailParam);
            
            // 根据tail参数截取结果
            if (tail && logLines.size() > lines) {
                logLines = logLines.subList(logLines.size() - lines, logLines.size());
            } else if (!tail && logLines.size() > lines) {
                logLines = logLines.subList(0, lines);
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("username", username);
            response.put("lines", logLines);
            response.put("fileSize", fileSize);
            response.put("totalLines", logLines.size());
            ctx.status(200).json(response);
        } catch (NumberFormatException e) {
            ctx.status(400).json(createErrorResponse("无效的参数: " + e.getMessage(), "Error.Request.InvalidParameter"));
        } catch (Exception e) {
            ctx.status(500).json(createErrorResponse("处理请求时发生错误: " + e.getMessage(), "Error.System.UnexpectedError"));
        }
    }
    
    /**
     * 安全读取文件行
     */
    private List<String> safeReadLines(File file, int maxLines) throws IOException {
        List<String> lines = new ArrayList<>();
        
        // 如果文件大于10MB，使用高效的尾部读取方法
        if (file.length() > 10 * 1024 * 1024) { // 10MB
            try (java.io.RandomAccessFile raf = new java.io.RandomAccessFile(file, "r")) {
                // 设置缓冲区大小，尝试读取足够多的行但不会占用太多内存
                final int BUFFER_SIZE = 8192;
                StringBuilder sb = new StringBuilder();
                byte[] buf = new byte[BUFFER_SIZE];
                
                // 从文件末尾开始向前读取
                long fileLength = raf.length();
                long pos = fileLength - 1;
                int linesCount = 0;
                
                // 当还有内容可读并且未达到所需行数
                while (pos > 0 && linesCount < maxLines) {
                    long readSize = Math.min(BUFFER_SIZE, pos + 1);
                    pos -= readSize;
                    raf.seek(pos);
                    int bytesRead = raf.read(buf, 0, (int)readSize);
                    
                    if (bytesRead <= 0) break;
                    
                    // 处理读取的字节
                    for (int i = bytesRead - 1; i >= 0; i--) {
                        if (buf[i] == '\n') {
                            linesCount++;
                            if (linesCount > maxLines) break;
                        }
                    }
                }
                
                // 重新从确定的位置开始读取
                raf.seek(Math.max(0, pos));
                String line;
                try (java.io.BufferedReader br = new java.io.BufferedReader(new java.io.InputStreamReader(new java.io.FileInputStream(raf.getFD())))) {
                    int count = 0;
                    while ((line = br.readLine()) != null && count < maxLines) {
                        lines.add(line);
                        count++;
                    }
                }
                
                return lines;
            } catch (Exception e) {
                // 如果高效方法失败，回退到传统方法但限制读取行数
                System.err.println("高效读取方法失败: " + e.getMessage() + "，尝试使用传统方法...");
            }
        }
        
        // 对于小文件或高效方法失败的情况，使用传统方法但确保只读取所需的行数
        try (java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(new java.io.FileInputStream(file), java.nio.charset.StandardCharsets.UTF_8))) {
            String line;
            int count = 0;
            while ((line = reader.readLine()) != null && count < maxLines) {
                lines.add(line);
                count++;
            }
            return lines;
        } catch (Exception e) {
            // 如果UTF-8失败，尝试使用系统默认编码
            try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(file))) {
                String line;
                int count = 0;
                while ((line = reader.readLine()) != null && count < maxLines) {
                    lines.add(line);
                    count++;
                }
                return lines;
            }
        }
    }
    
    /**
     * 备用读取方法，使用BufferedReader
     */
    private List<String> fallbackReadLines(File file) throws IOException {
        List<String> lines = new ArrayList<>();
        try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
        }
        return lines;
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
    public void getUsersList(Context ctx) {
        Object userObj = ctx.attribute("user");
        if (userObj == null || !(userObj instanceof User)) {
            ctx.status(401).json(createErrorResponse("未授权或会话已过期", "Error.Security.Unauthorized"));
            return;
        }
        
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
                    // 使用安全的方法读取文件
                    List<String> fileLines = new ArrayList<>();
                    try {
                        fileLines = safeReadLines(logFile, 1000); // 读取最多1000行
                    } catch (Exception e) {
                        try {
                            fileLines = fallbackReadLines(logFile);
                        } catch (Exception ex) {
                            System.err.println("无法读取文件 " + logFile.getName() + ": " + ex.getMessage());
                            continue; // 跳过这个文件
                        }
                    }
                    
                    if (fileLines.isEmpty()) {
                        continue; // 跳过空文件
                    }
                    
                    // 最后一次请求时间就是日志的最后一条时间记录
                    for (int i = fileLines.size() - 1; i >= 0; i--) {
                        String line = fileLines.get(i);
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
                    for (int i = fileLines.size() - 1; i >= 0; i--) {
                        String line = fileLines.get(i);
                        if (LOGIN_PATTERN.matcher(line).find() && i > 0) {
                            // 找到登录请求，获取其时间戳
                            String previousLine = fileLines.get(i - 1);
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
                } catch (Exception e) {
                    // 忽略单个文件的错误，继续处理其他文件
                    System.err.println("处理文件失败: " + logFile.getName() + " - " + e.getMessage());
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
} 