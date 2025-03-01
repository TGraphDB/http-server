package app;

import io.javalin.Javalin;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Map;
import java.util.HashMap;
import java.util.List;

import handlers.LabelHandler;
import handlers.NodeHandler;
import handlers.RelationshipHandler;
import handlers.TgraphHandler;
import handlers.PropertyHandler;

// 着重了解一下org.neo4j.tooling.GlobalGraphOperations

import service.UserService;
import tgraph.Tgraph;
import service.User;
import util.PasswordUtil;
import service.SecurityConfig;

// label和dynamiclabel的区别
import org.neo4j.graphdb.GraphDatabaseService;

public class Application {
    private static UserService userService = new UserService();
    private static Tgraph tgraph = new Tgraph();
    /*
    1. 当应用程序启动时，Application 类被加载
    2. 类加载时会执行静态字段的初始化
    3. tgraph.startDb() 被调用，启动数据库
    4. 数据库实例被赋值给 graphDb 静态字段
     */
    // database不是这样创建的 而应该是通过rest api调用去创建的 可以用一个变量记录当前的user以及当前的数据库
    public static GraphDatabaseService graphDb = null;

    // 创建处理器实例
    private static RelationshipHandler relationshipHandler = new RelationshipHandler(graphDb);
    private static NodeHandler nodeHandler = new NodeHandler(graphDb);
    private static LabelHandler labelHandler = new LabelHandler(graphDb);
    private static PropertyHandler propertyHandler = new PropertyHandler(graphDb);
    private static TgraphHandler TgraphHandler = new TgraphHandler(graphDb, tgraph);

    // 在创建或启动数据库后更新所有handler的graphDb
    public static void updateHandlers(GraphDatabaseService newGraphDb) {
        relationshipHandler.setGraphDb(newGraphDb);
        nodeHandler.setGraphDb(newGraphDb);
        labelHandler.setGraphDb(newGraphDb);
        propertyHandler.setGraphDb(newGraphDb);
        TgraphHandler.setGraphDb(newGraphDb);
    }
    
    public static void main(String[] args) {

        Javalin app = Javalin.create(config -> {
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
        }).start(7474);

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

        // 通过ID获取关系API
        app.get("/db/data/relationship/{id}", relationshipHandler::getRelationship);

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

        // 获取关系类型
        app.get("/db/data/relationship/types", relationshipHandler::getRelationshipTypes);

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