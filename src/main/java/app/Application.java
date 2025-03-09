package app;

import io.javalin.Javalin;

import java.util.Base64;
import java.util.Collections;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;
import java.nio.charset.StandardCharsets;

import handlers.LabelHandler;
import handlers.NodeHandler;
import handlers.RelationshipHandler;
import handlers.TgraphHandler;
import handlers.PropertyHandler;
import handlers.UserLogHandler;

// 着重了解一下org.neo4j.tooling.GlobalGraphOperations

import service.UserService;
import service.User;
import service.SessionManager;
import service.SystemMonitorService;
import util.PasswordUtil;
import service.SecurityConfig;
import util.ServerConfig;
import tgraph.DBSpace;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.util.thread.QueuedThreadPool;


// label和dynamiclabel的区别

public class Application {
    private static UserService userService = new UserService();

    // 创建处理器实例
    private static RelationshipHandler relationshipHandler = new RelationshipHandler();
    private static NodeHandler nodeHandler = new NodeHandler();
    private static LabelHandler labelHandler = new LabelHandler();
    private static PropertyHandler propertyHandler = new PropertyHandler();
    private static TgraphHandler TgraphHandler = new TgraphHandler();
    private static UserLogHandler userLogHandler = new UserLogHandler();
    private static SystemMonitorService systemMonitorService = new SystemMonitorService();
    
    
    public static void main(String[] args) {
        // 加载配置文件
        String configPath = System.getProperty("config.path", "config/server-config.properties");
        ServerConfig.loadAndApplyConfig(configPath);
        
        
        // 获取配置的端口
        int port = ServerConfig.getInt("org.neo4j.server.webserver.port", 7474);
        String host = ServerConfig.getString("org.neo4j.server.webserver.address", "0.0.0.0");
        int maxThreads = ServerConfig.getInt("org.neo4j.server.webserver.maxthreads", 200);
        boolean httpLogEnabled = ServerConfig.getBoolean("org.neo4j.server.http.log.enabled", true);
        int transactionTimeout = ServerConfig.getInt("org.neo4j.server.transaction.timeout", 60);
        // transactionTimeout参数for long running cypher queries

        // 创建Javalin应用
        Javalin app = Javalin.create(config -> {

            // 如果启用HTTP日志，则配置Javalin日志
            if (httpLogEnabled) {
                config.enableDevLogging();
            }

            // 配置 CORS
            config.enableCorsForAllOrigins(); // 允许所有源的跨域请求

            // 或者，针对特定域名配置 CORS
            // config.enableCorsForOrigin("http://localhost:3000", "https://example.com");

            // 更细粒度的 CORS 配置
            // config.enableCorsForOrigin("http://localhost:3000")
            //       .allowCredentials()
            //       .allowHeader("Content-Type")
            //       .allowHeader("Authorization")
            //       .allowMethod("GET")
            //       .allowMethod("POST")
            //       .allowMethod("DELETE")
            //       .allowMethod("PUT")
            //       .allowMethod("PATCH");

            // 设置最大线程数
            config.server(() -> {
                QueuedThreadPool threadPool = new QueuedThreadPool(maxThreads);
                Server server = new Server(threadPool); // 使用构造函数设置线程池
                return server;
            });

            
            config.accessManager((handler, ctx, permittedRoles) -> {
                // 如果认证被禁用，直接允许访问
                if (!SecurityConfig.isAuthEnabled()) {
                    handler.handle(ctx);
                    return;
                }
                
                // 公共路径无需认证
                if (ctx.path().startsWith("/user/login") || 
                    ctx.path().startsWith("/user/logout") || 
                    ctx.path().startsWith("/user/register") ||
                    ctx.path().startsWith("/system/resources") ||
                    ctx.path().startsWith("/user") ||
                    ctx.path().startsWith("/databases")) {
                    handler.handle(ctx);
                    return;
                }
                
                // 尝试从cookie中获取会话ID
                String sessionId = ctx.cookie("sessionId");
                if (sessionId != null) {
                    // 验证会话
                    String username = SessionManager.validateSession(sessionId);
                    if (username != null) {
                        // 会话有效
                        User user = userService.getUserStatus(username);
                        if (user != null) {
                            // 设置当前用户
                            ctx.attribute("user", user);
                            
                            // 如果密码需要更改，并且不是访问密码更改页面，则返回403
                            if (user.isPasswordChangeRequired() && 
                                !ctx.path().equals("/user/" + username + "/password")) {
                                Map<String, Object> response = new HashMap<>();
                                response.put("password_change", "http://localhost:" + ctx.port() + "/user/" + username + "/password");
                                response.put("errors", Collections.singletonList(new ErrorResponse(
                                    "用户需要修改密码。",
                                    "Neo.ClientError.Security.AuthorizationFailed"
                                )));
                                
                                ctx.status(403)
                                   .json(response);
                                return;
                            }
                            
                            handler.handle(ctx);
                            return;
                        }
                    }
                }
                
                String authHeader = ctx.header("Authorization");
                if (authHeader == null) {
                    Map<String, Object> response = new HashMap<>();
                    response.put("errors", Collections.singletonList(new ErrorResponse(
                        "未提供授权信息。请登录或提供认证头。",
                        "Neo.ClientError.Security.AuthorizationFailed"
                    )));
                    
                    ctx.status(401)
                       .header("WWW-Authenticate", "None")
                       .json(response);
                    return;
                }
                
                try {
                    String[] credentials = extractCredentials(authHeader);
                    String username = credentials[0];
                    String password = credentials[1];
                    
                    User user = userService.getUserStatus(username);
                    if (user == null || !PasswordUtil.checkPassword(password, user.getPasswordHash())) {
                        Map<String, Object> response = new HashMap<>();
                        response.put("errors", Collections.singletonList(new ErrorResponse(
                            "Invalid username or password.",
                            "Neo.ClientError.Security.AuthorizationFailed"
                        )));
                        
                        ctx.status(401)
                           .header("WWW-Authenticate", "None")
                           .json(response);
                        return;
                    }
                    
                    // 将认证成功的用户保存到ctx属性中，以便后续处理
                    ctx.attribute("user", user);
                    
                    if (user.isPasswordChangeRequired()) {
                        Map<String, Object> response = new HashMap<>();
                        response.put("password_change", "http://localhost:" + ctx.port() + "/user/" + username + "/password");
                        response.put("errors", Collections.singletonList(new ErrorResponse(
                            "User is required to change their password.",
                            "Neo.ClientError.Security.AuthorizationFailed"
                        )));
                        
                        ctx.status(403)
                           .json(response);
                        return;
                    }
                    
                    handler.handle(ctx);
                } catch (Exception e) {
                    ctx.status(401).json(new ErrorResponse(
                        "Invalid authorization header.",
                        "Neo.ClientError.Security.AuthorizationFailed"
                    ));
                }
            });
        }).start(host, port);

        // 设置事务超时时间
        // 添加中间件以处理事务超时
        app.before(ctx -> {
            // 为每个请求生成唯一 ID
            String requestId = UUID.randomUUID().toString();
            ctx.attribute("requestId", requestId);
            
            // 尝试从cookie中获取会话ID和用户名
            String sessionId = ctx.cookie("sessionId");
            if (sessionId != null) {
                String username = SessionManager.validateSession(sessionId);
                if (username != null) {
                    // 会话有效，设置当前用户
                    User user = userService.getUserStatus(username);
                    if (user != null) {
                        ctx.attribute("user", user);
                    }
                } 
            } 
            
            // 记录请求开始时间
            ctx.attribute("requestStartTime", System.currentTimeMillis());
            
            
        });
        
        // 目前只能在结束后再计算是否超时
        app.after(ctx -> {
            // 在请求完成后的处理
        });

        // 列出所有属性键API
        app.get("/db/data/propertykeys", propertyHandler::getAllPropertyKeys);
        
        // 添加数据API
        app.get("/db/data/", ctx -> {
            Map<String, Object> response = new HashMap<>();
            response.put("extensions", new HashMap<>());
            response.put("node", "http://localhost:" + ctx.port() + "/db/data/node");
            response.put("node_index", "http://localhost:" + ctx.port() + "/db/data/index/node");
            response.put("relationship_index", "http://localhost:" + ctx.port() + "/db/data/index/relationship");
            response.put("extensions_info", "http://localhost:" + ctx.port() + "/db/data/ext");
            response.put("relationship_types", "http://localhost:" + ctx.port() + "/db/data/relationship/types");
            response.put("batch", "http://localhost:" + ctx.port() + "/db/data/batch");
            response.put("cypher", "http://localhost:" + ctx.port() + "/db/data/cypher");
            response.put("indexes", "http://localhost:" + ctx.port() + "/db/data/schema/index");
            response.put("constraints", "http://localhost:" + ctx.port() + "/db/data/schema/constraint");
            response.put("transaction", "http://localhost:" + ctx.port() + "/db/data/transaction");
            response.put("node_labels", "http://localhost:" + ctx.port() + "/db/data/labels");
            response.put("neo4j_version", "2.3.12");
            
            ctx.status(200).json(response);
        });
        
        // 用户状态API
        app.get("/user/{username}/status", ctx -> {
            String username = ctx.pathParam("username");
            
            // 验证当前用户是否有权查看此用户信息
            Object userObj = ctx.attribute("user");
            if (userObj == null || !(userObj instanceof User) || 
                !((User)userObj).getUsername().equals(username)) {
                ctx.status(403).json(new ErrorResponse(
                    "无权访问此用户信息。",
                    "Neo.ClientError.Security.Forbidden"
                ));
                return;
            }
            
            User user = userService.getUserStatus(username);
            if (user != null) {
                Map<String, Object> response = new HashMap<>();
                response.put("username", username);
                response.put("password_change", "http://localhost:" + ctx.port() + "/user/" + username + "/password");
                response.put("password_change_required", user.isPasswordChangeRequired());
                
                ctx.status(200)
                   .json(response);
            } else {
                ctx.status(404);
            }
        });
        
        // 密码修改API
        app.post("/user/{username}/password", ctx -> {
            String username = ctx.pathParam("username");
            
            // 验证当前用户是否有权更改此用户密码
            Object userObj = ctx.attribute("user");
            if (userObj == null || !(userObj instanceof User) || 
                !((User)userObj).getUsername().equals(username)) {
                ctx.status(403).json(new ErrorResponse(
                    "无权更改此用户的密码。",
                    "Neo.ClientError.Security.Forbidden"
                ));
                return;
            }
            
            PasswordChangeRequest request;
            try {
                request = ctx.bodyAsClass(PasswordChangeRequest.class);
            } catch (Exception e) {
                ctx.status(400).json(new ErrorResponse(
                    "无效的密码更改请求格式。",
                    "Neo.ClientError.Request.InvalidFormat"
                ));
                return;
            }
            
            String currentPassword = request.getCurrentPassword();
            String newPassword = request.getNewPassword();
            
            if (userService.changePassword(username, currentPassword, newPassword)) {
                ctx.status(200);
            } else {
                ctx.status(400).json(new ErrorResponse(
                    "密码更改请求无效，或新密码与当前密码相同。",
                    "Neo.ClientError.Security.InvalidPassword"
                ));
            }
        });

        // 创建节点API
        app.post("/db/data/node", nodeHandler::createNode);

        // 获取节点API(存在和不存在的)
        app.get("/db/data/node/{id}", nodeHandler::getNode);

        // 删除节点API
        app.delete("/db/data/node/{id}", nodeHandler::deleteNode);

        // 移动至下方

        // 创建关系API
        app.post("/db/data/node/{id}/relationships", relationshipHandler::createRelationship);

        // 删除关系API
        app.delete("/db/data/relationship/{id}", relationshipHandler::deleteRelationship);

        // 获取关系上所有属性API
        app.get("/db/data/relationship/{id}/properties", relationshipHandler::getProperties);

        // 设置关系上的所有属性
        app.put("/db/data/relationship/{id}/properties", relationshipHandler::setProperties);

        // 获取关系上的单个属性
        app.get("/db/data/relationship/{id}/properties/{key}", relationshipHandler::getProperty);

        // 设置关系上的单个属性
        app.put("/db/data/relationship/{id}/properties/{key}", relationshipHandler::setProperty);

        // 获取所有关系（有关系和没有关系的）
        app.get("/db/data/node/{id}/relationships/all", relationshipHandler::getAllRelationships);

        // 获取传入的关系
        app.get("/db/data/node/{id}/relationships/in", relationshipHandler::getIncomingRelationships);

        // 获取传出的关系
        app.get("/db/data/node/{id}/relationships/out", relationshipHandler::getOutgoingRelationships);

        // 获取指定类型的关系 tx.success()是在try里，没有包含在整个transaction里
        app.get("/db/data/node/{id}/relationships/all/{typeString}", relationshipHandler::getRelationshipsByTypes);

        // 获取关系类型 和下面的产生路径冲突 将types识别成了{id} 将将具体路径放在模式匹配的路径前面可以确保它会被优先匹配
        /*
        ai说以下也会冲突
            // 先定义具体路径
            app.get("/db/data/labels", labelHandler::getAllLabels);

            // 再定义参数路径
            app.get("/db/data/label/{labelName}/nodes", labelHandler::getNodesWithLabel);
        */

        /*
            1. /db/data/relationship/types 是一个具体的路径
            2. /db/data/relationship/{id} 是一个模式匹配的路径
            3. 如果不调整顺序，types 会被错误地识别为 {id} 的值
            4. 将具体路径放在前面可以确保它会被优先匹配
        */
        app.get("/db/data/relationship/types", relationshipHandler::getRelationshipTypes);

        // 通过ID获取关系API
        app.get("/db/data/relationship/{id}", relationshipHandler::getRelationship);

        // 在节点上设置单个属性
        app.put("/db/data/node/{id}/properties/{key}", nodeHandler::setProperty);

        // 更新节点的所有属性
        app.put("/db/data/node/{id}/properties", nodeHandler::updateAllProperties);

        // 获取节点的所有属性
        app.get("/db/data/node/{id}/properties", nodeHandler::getAllProperties);

        // 获取节点的单个属性
        app.get("/db/data/node/{id}/properties/{key}", nodeHandler::getProperty);

        // 删除节点的所有属性
        app.delete("/db/data/node/{id}/properties", nodeHandler::deleteAllProperties);

        // 删除节点的单个属性
        app.delete("/db/data/node/{id}/properties/{key}", nodeHandler::deleteProperty);

        // 从关系中删除所有属性
        app.delete("/db/data/relationship/{id}/properties", relationshipHandler::deleteAllProperties);

        // 从关系中删除单个属性
        app.delete("/db/data/relationship/{id}/properties/{key}", relationshipHandler::deleteProperty);

        // 向节点添加标签
        app.post("/db/data/node/{id}/labels", nodeHandler::addLabels);

        // 替换节点上的所有标签
        app.put("/db/data/node/{id}/labels", nodeHandler::replaceLabels);

        // 从节点中删除标签
        app.delete("/db/data/node/{id}/labels/{labelName}", nodeHandler::removeLabel);

        // 获取节点的所有标签
        app.get("/db/data/node/{id}/labels", nodeHandler::getAllLabels);

        // 获取具有特定标签的所有节点，支持可选的属性过滤
        app.get("/db/data/label/{labelName}/nodes", labelHandler::getNodesWithLabel);

        // 列出所有标签
        app.get("/db/data/labels", labelHandler::getAllLabels);

        // 获取节点的度数（各种场景）
        app.get("/db/data/node/{id}/degree/*", nodeHandler::getDegree);

        // 创建数据库
        app.post("/db/data/database/{databaseName}/create", TgraphHandler::createDatabase);

        // 启动数据库
        app.post("/db/data/database/{databaseName}/start", TgraphHandler::startDatabase);

        // 删除数据库
        app.delete("/db/data/database/{databaseName}", TgraphHandler::deleteDatabase);
        
        // 关闭数据库 由于一个时间只能有一个数据库被打开 所以不用传入{databaseName}
        app.post("/db/data/database", TgraphHandler::shutdownDatabase);

        // 备份数据库
        app.post("/db/data/database/{databaseName}/backup", TgraphHandler::backupDatabase);

        // 恢复数据库
        app.post("/db/data/database/{databaseName}/restore", TgraphHandler::restoreDatabase);

        // 获取数据库路径
        app.get("/db/data/database/{databaseName}/path", TgraphHandler::getDatabasePath);

        // 获取数据库状态
        app.get("/db/data/database/status", TgraphHandler::getDatabaseStatus);

        // 添加数据库空间统计API
        app.get("/databases/{dbname}/space", ctx -> {
            String dbname = ctx.pathParam("dbname");
            
            // 验证当前用户是否有权限查看此数据库信息
            Object userObj = ctx.attribute("user");
            if (userObj == null || !(userObj instanceof User)) {
                ctx.status(401).json(new ErrorResponse(
                    "未授权或会话已过期",
                    "Neo.ClientError.Security.Unauthorized"
                ));
                return;
            }
            
            String username = ((User) userObj).getUsername();
            Map<String, Object> response = DBSpace.getSpaceStatsResponse(username, dbname);
            ctx.status(200).json(response);
        });

        // 添加系统资源监控API
        app.get("/system/resources", ctx -> {
            Map<String, Object> resources = systemMonitorService.getSystemResources();
            // 格式化CPU使用率为百分比字符串
            Double procCpu = (Double)resources.get("processCpuLoad");
            Double sysCpu = (Double)resources.get("systemCpuLoad");
            resources.put("processCpuLoad", String.format("%.2f%%", procCpu * 100));
            resources.put("systemCpuLoad", String.format("%.2f%%", sysCpu * 100));
            
            ctx.status(200).json(resources);
        });

        // 添加用户日志查看API
        app.get("/user/logs", userLogHandler::getUserLog);

        // 在 Javalin.create 配置中添加
        app.before(ctx -> {
            // 为每个请求生成唯一 ID
            String requestId = UUID.randomUUID().toString();
            // 存储请求 ID 在上下文中，以便后续使用
            ctx.attribute("requestId", requestId);
            // 记录请求开始
            RequestTracker.startRequest(requestId, ctx.path(), ctx.method());
        });

        app.after(ctx -> {
            // 获取请求 ID
            String requestId = ctx.attribute("requestId");
            // 记录请求结束
            if (requestId != null) {
                RequestTracker.endRequest(requestId);
            }
        });

        // 添加一个 API 端点，用于查询当前正在执行的请求列表
        app.get("/admin/active-requests", ctx -> {
            // 检查是否有管理员权限
            ctx.json(RequestTracker.getActiveRequests());
        });

        // 用户登录API
        app.post("/user/login", ctx -> {
            LoginRequest loginRequest;
            try {
                loginRequest = ctx.bodyAsClass(LoginRequest.class);
            } catch (Exception e) {
                ctx.status(400).json(new ErrorResponse(
                    "无效的登录请求格式。",
                    "Neo.ClientError.Request.InvalidFormat"
                ));
                return;
            }
            
            String username = loginRequest.getUsername();
            String password = loginRequest.getPassword();
            boolean rememberMe = loginRequest.isRememberMe();
            
            // 验证用户
            User user = userService.getUserStatus(username);
            if (user == null || !PasswordUtil.checkPassword(password, user.getPasswordHash())) {
                ctx.status(401).json(new ErrorResponse(
                    "用户名或密码无效。",
                    "Neo.ClientError.Security.AuthorizationFailed"
                ));
                return;
            }
            
            // 创建会话
            String sessionId = SessionManager.createSession(username, rememberMe);
            
            // 设置会话cookie
            int maxAge = rememberMe ? 7 * 24 * 60 * 60 : -1; // "记住我"设置7天，否则浏览器关闭时失效
            ctx.cookie("sessionId", sessionId, maxAge);
            
            // 为用户初始化HTTP日志记录器
            HttpLogger.initializeLogger(username);
            
            // 返回登录成功的响应
            Map<String, Object> response = new HashMap<>();
            response.put("username", username);
            
            if (user.isPasswordChangeRequired()) {
                response.put("password_change_required", true);
                response.put("password_change_url", "http://localhost:" + ctx.port() + "/user/" + username + "/password");
            } else {
                response.put("password_change_required", false);
            }
            
            ctx.status(200).json(response);
        });
        
        // 用户登出API
        app.post("/user/logout", ctx -> {
            String sessionId = ctx.cookie("sessionId");
            if (sessionId != null) {
                // 获取用户名以关闭日志记录器
                String username = SessionManager.validateSession(sessionId);
                if (username != null) {
                    HttpLogger.closeLogger(username);
                }
                
                SessionManager.invalidateSession(sessionId);
                ctx.removeCookie("sessionId");
            }
            ctx.status(200).json(Collections.singletonMap("message", "登出成功"));
        });

        // 用户注册API（无需登录）
        app.post("/user/register", ctx -> {
            RegisterUserRequest registerRequest;
            try {
                registerRequest = ctx.bodyAsClass(RegisterUserRequest.class);
            } catch (Exception e) {
                ctx.status(400).json(new ErrorResponse(
                    "无效的注册请求格式。",
                    "Neo.ClientError.Request.InvalidFormat"
                ));
                return;
            }
            
            // 验证用户注册
            String result = userService.registerUser(
                registerRequest.getUsername(),
                registerRequest.getPassword()
            );
            
            if (result != null) {
                // 注册失败
                ctx.status(400).json(new ErrorResponse(
                    result,
                    "Neo.ClientError.Request.Invalid"
                ));
            } else {
                // 注册成功，自动创建会话并设置cookie
                String sessionId = SessionManager.createSession(
                    registerRequest.getUsername(), 
                    registerRequest.isRememberMe()
                );
                
                // 设置会话cookie
                int maxAge = registerRequest.isRememberMe() ? 7 * 24 * 60 * 60 : -1;
                ctx.cookie("sessionId", sessionId, maxAge);
                
                // 返回注册成功的响应
                Map<String, Object> response = new HashMap<>();
                response.put("username", registerRequest.getUsername());
                response.put("message", "注册成功并自动登录");
                ctx.status(201).json(response);
            }
        });
    }
    
    private static String[] extractCredentials(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Basic ")) {
            return new String[] { "", "" };
        }
        try {
            String base64Credentials = authHeader.substring("Basic ".length()).trim();
            byte[] credDecoded = Base64.getDecoder().decode(base64Credentials);
            String credentials = new String(credDecoded, StandardCharsets.UTF_8);
            return credentials.split(":", 2);
        } catch (Exception e) {
            return new String[] { "", "" };
        }
    }
    
    private static class ErrorResponse {
        private String message;
        private String code;
        
        public ErrorResponse(String message, String code) {
            this.message = message;
            this.code = code;
        }
        
        // Getters
        public String getMessage() { return message; }
        public String getCode() { return code; }
    }
    
    private static class LoginRequest {
        private String username;
        private String password;
        private boolean rememberMe;
        
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
        public boolean isRememberMe() { return rememberMe; }
        public void setRememberMe(boolean rememberMe) { this.rememberMe = rememberMe; }
    }
    
    private static class PasswordChangeRequest {
        private String currentPassword;
        private String newPassword;
        
        public String getCurrentPassword() { return currentPassword; }
        public void setCurrentPassword(String currentPassword) { this.currentPassword = currentPassword; }
        public String getNewPassword() { return newPassword; }
        public void setNewPassword(String newPassword) { this.newPassword = newPassword; }
    }

    // 用户注册请求类
    private static class RegisterUserRequest {
        private String username;
        private String password;
        private boolean rememberMe;
        
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
        public boolean isRememberMe() { return rememberMe; }
        public void setRememberMe(boolean rememberMe) { this.rememberMe = rememberMe; }
    }
}