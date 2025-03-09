package app;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.FileAppender;
import org.slf4j.LoggerFactory;

import java.io.File;

/**
 * HTTP日志记录器，将Javalin的HTTP请求日志同时输出到控制台和用户特定的日志文件
 */
public class HttpLogger {
    private static final String LOG_DIRECTORY = "target/logs";
    private static final Logger javalinLogger = (Logger) LoggerFactory.getLogger("io.javalin.Javalin");
    
    /**
     * 为指定用户初始化HTTP日志记录器
     * @param username 用户名，用于日志文件命名
     */
    public static void initializeLogger(String username) {
        try {
            // 创建日志目录（如果不存在）
            File logDir = new File(LOG_DIRECTORY);
            if (!logDir.exists()) {
                logDir.mkdirs();
            }
            
            // 获取Logback上下文
            LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
            
            // 创建文件appender
            FileAppender<ILoggingEvent> fileAppender = new FileAppender<>();
            fileAppender.setContext(loggerContext);
            fileAppender.setName("FILE-" + username);
            fileAppender.setFile(LOG_DIRECTORY + "/" + username + ".log");
            
            // 设置编码器
            PatternLayoutEncoder encoder = new PatternLayoutEncoder();
            encoder.setContext(loggerContext);
            encoder.setPattern("%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n");
            encoder.start();
            
            fileAppender.setEncoder(encoder);
            fileAppender.start();
            
            // 将appender添加到Javalin日志记录器
            javalinLogger.addAppender(fileAppender);
            
        } catch (Exception e) {
            System.err.println("Error initializing HTTP logger: " + e.getMessage());
        }
    }
    
    /**
     * 关闭指定用户的日志记录
     * @param username 用户名
     */
    public static void closeLogger(String username) {
        try {
            // 获取appender并停止
            Logger logger = (Logger) LoggerFactory.getLogger("io.javalin.Javalin");
            FileAppender<ILoggingEvent> appender = (FileAppender<ILoggingEvent>) logger.getAppender("FILE-" + username);
            if (appender != null) {
                appender.stop();
                logger.detachAppender(appender);
            }
        } catch (Exception e) {
            System.err.println("Error closing HTTP logger: " + e.getMessage());
        }
    }
} 