package util;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.OutputStreamWriter;
import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 用户日志工具类，用于将控制台输出重定向到用户自己的日志文件中
 */
public class UserLogger {
    private static final String LOGS_DIR = "target/logs";
    private static final Map<String, PrintStream> userLogStreams = new ConcurrentHashMap<>();
    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
    
    // 原始的系统输出流
    private static final PrintStream originalOut = System.out;
    private static final PrintStream originalErr = System.err;
    
    static {
        // 确保日志目录存在
        new File(LOGS_DIR).mkdirs();
        
        // 添加JVM关闭钩子，确保所有日志文件被正确关闭
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            synchronized (userLogStreams) {
                for (PrintStream stream : userLogStreams.values()) {
                    stream.flush();
                    stream.close();
                }
                userLogStreams.clear();
                
                // 恢复原始输出流
                System.setOut(originalOut);
                System.setErr(originalErr);
            }
        }));
    }
    
    /**
     * 设置当前线程的用户名，用于日志重定向
     * @param username 用户名
     */
    public static void setCurrentUser(String username) {
        Thread.currentThread().setName("user-" + username);
    }
    
    /**
     * 获取当前用户名
     * @return 当前用户名，如果无法确定，返回"system"
     */
    public static String getCurrentUser() {
        String threadName = Thread.currentThread().getName();
        if (threadName.startsWith("user-")) {
            return threadName.substring(5);
        }
        return "system";
    }
    
    /**
     * 为用户获取一个日志输出流
     * @param username 用户名
     * @return 日志输出流
     */
    public static PrintStream getUserLogStream(String username) {
        synchronized (userLogStreams) {
            PrintStream stream = userLogStreams.get(username);
            if (stream == null) {
                try {
                    // 确保用户日志目录存在
                    File userLogDir = new File(LOGS_DIR, username);
                    userLogDir.mkdirs();
                    
                    // 创建用户日志文件，使用UTF-8编码
                    File logFile = new File(userLogDir, "console.log");
                    stream = new PrintStream(
                        new FileOutputStream(logFile, true), 
                        true, 
                        StandardCharsets.UTF_8.name()
                    );
                    userLogStreams.put(username, stream);
                } catch (IOException e) {
                    originalErr.println("无法创建用户日志文件: " + e.getMessage());
                    return originalOut; // 失败时返回原始输出流
                }
            }
            return stream;
        }
    }
    
    /**
     * 启用用户日志记录
     */
    public static void enableUserLogging() {
        // 替换系统标准输出和错误输出
        System.setOut(new UserAwareOutputStream(originalOut, false));
        System.setErr(new UserAwareOutputStream(originalErr, true));
        
        // 输出一条启动消息
        System.out.println("=== 用户日志系统已启动 ===");
    }
    
    /**
     * 用户感知的输出流，根据当前线程的用户名重定向输出
     */
    private static class UserAwareOutputStream extends PrintStream {
        private final PrintStream originalStream;
        private final boolean isError;
        private StringBuilder lineBuffer = new StringBuilder(1024);
        
        public UserAwareOutputStream(PrintStream originalStream, boolean isError) {
            super(originalStream);
            this.originalStream = originalStream;
            this.isError = isError;
        }
        
        @Override
        public void write(int b) {
            char c = (char) b;
            lineBuffer.append(c);
            
            // 如果是换行符，处理完整行
            if (c == '\n') {
                processLine();
            }
            
            originalStream.write(b);
        }
        
        @Override
        public void write(byte[] buf, int off, int len) {
            // 转换为字符串并追加到缓冲区
            String str = new String(buf, off, len, StandardCharsets.UTF_8);
            lineBuffer.append(str);
            
            // 检查是否包含换行符
            if (str.indexOf('\n') >= 0) {
                processLine();
            }
            
            originalStream.write(buf, off, len);
        }
        
        private void processLine() {
            if (lineBuffer.length() == 0) {
                return;
            }
            
            String line = lineBuffer.toString();
            lineBuffer.setLength(0); // 清空缓冲区
            
            // 处理可能的多行内容
            String[] lines = line.split("\\n", -1);
            for (int i = 0; i < lines.length - 1; i++) {
                if (!lines[i].isEmpty()) {
                    writeToUserLog(lines[i]);
                }
            }
            
            // 最后一段可能不以换行符结尾
            if (!lines[lines.length - 1].isEmpty()) {
                lineBuffer.append(lines[lines.length - 1]);
            }
        }
        
        private void writeToUserLog(String message) {
            String user = getCurrentUser();
            String timestamp = dateFormat.format(new Date());
            String logLevel = isError ? "ERROR" : "INFO";
            String formattedMessage = String.format("[%s] [%s] [%s] %s", 
                timestamp, logLevel, user, message);
            
            // 如果不是系统用户，输出到用户的日志文件
            if (!"system".equals(user)) {
                PrintStream userStream = getUserLogStream(user);
                if (userStream != originalStream) {
                    userStream.println(formattedMessage);
                }
            }
        }
        
        @Override
        public void println(String x) {
            lineBuffer.append(x);
            processLine();
            originalStream.println(x);
        }
        
        @Override
        public void flush() {
            // 如果缓冲区不为空，处理剩余内容
            if (lineBuffer.length() > 0) {
                writeToUserLog(lineBuffer.toString());
                lineBuffer.setLength(0);
            }
            originalStream.flush();
        }
    }
} 