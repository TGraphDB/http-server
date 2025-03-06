package app;

import io.javalin.Javalin;

import java.util.Base64;
import java.util.Collections;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;

import handlers.LabelHandler;
import handlers.NodeHandler;
import handlers.RelationshipHandler;
import handlers.TgraphHandler;
import handlers.PropertyHandler;

// 着重了解一下org.neo4j.tooling.GlobalGraphOperations

import service.UserService;
import service.User;
import util.PasswordUtil;
import service.SecurityConfig;
import util.ServerConfig;

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
        // 下一步是应用maxThreads和transactionTimeout参数到代码中

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

            // 可以考虑把这些放到before里面
            config.accessManager((handler, ctx, permittedRoles) -> {
                // 如果认证被禁用，直接允许访问
                if (!SecurityConfig.isAuthEnabled()) {
                    handler.handle(ctx);
                    return;
                }
                
                if (ctx.path().startsWith("/user/")) {
                    handler.handle(ctx);
                    return;
                }
                
                String authHeader = ctx.header("Authorization");
                if (authHeader == null) {
                    Map<String, Object> response = new HashMap<>();
                    response.put("errors", Collections.singletonList(new ErrorResponse(
                        "No authorization header supplied.",
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
            ctx.attribute("startTime", System.currentTimeMillis());
        });
        
        // 目前只能在结束后再计算是否超时
        app.after(ctx -> {
            Long startTime = ctx.attribute("startTime");
            if (startTime != null) {
                long endTime = System.currentTimeMillis();
                if (endTime - startTime > transactionTimeout * 1000) {
                    ctx.status(408).json(new ErrorResponse("Request timeout", "Neo.ClientError.RequestTimeout"));
                }
            }
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
        app.get("/user/{username}", ctx -> {
            String username = ctx.pathParam("username");
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
            String newPassword = ctx.bodyAsClass(PasswordChangeRequest.class).getPassword();
            
            // 从Authorization头获取当前密码
            String[] credentials = extractCredentials(ctx.header("Authorization"));
            String currentPassword = credentials[1];
            
            if (userService.changePassword(username, currentPassword, newPassword)) {
                ctx.status(200);
            } else {
                ctx.status(400).json(new ErrorResponse(
                    "Invalid password change request or new password same as current password.",
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
    }
    
    private static String[] extractCredentials(String authHeader) {
        if (!authHeader.startsWith("Basic ")) {
            throw new IllegalArgumentException("Invalid authorization header");
        }
        
        String base64Credentials = authHeader.substring("Basic ".length());
        String credentials = new String(Base64.getDecoder().decode(base64Credentials));
        return credentials.split(":", 2);
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
    
    private static class PasswordChangeRequest {
        private String password;
        
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
    }
}