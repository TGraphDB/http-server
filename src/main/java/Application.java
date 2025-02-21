import io.javalin.Javalin;
import java.util.Base64;
import java.util.Collections;
import java.util.Map;
import java.util.HashMap;

import org.neo4j.graphdb.Transaction;
import service.UserService;
import model.User;
import util.PasswordUtil;
import config.SecurityConfig;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;
import config.NodeIdManager;


public class Application {
    private static final UserService userService = new UserService();
    private static GraphDatabaseService graphDb;
    private static final String DB_PATH = "target/neo4j-hello-db";
    static {
        graphDb = new GraphDatabaseFactory().newEmbeddedDatabase( DB_PATH );
    }

    static{NodeIdManager.loadNodeId();} 
    static int nodeid = NodeIdManager.getNodeId();
    
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
        
        // 列出所有属性键API
        app.get("/db/data/propertykeys", ctx -> {
        
        });

        // 创建节点API
        app.post("/db/data/node", ctx -> {
            // 获取请求体中的属性
            // 判断请求体是否为空
            Map<String, Object> properties;
            if (ctx.body().isEmpty()) {
                properties = new HashMap<>();
            }
            else {
                properties = ctx.bodyAsClass(Map.class);
            }

            // 创建节点并设置属性
            try (Transaction tx = graphDb.beginTx()) {
                nodeid++;
                NodeIdManager.saveNodeId(nodeid);
                Node node = graphDb.createNode();
                
                // 设置固定的nodeid属性
                node.setProperty("nodeid", nodeid);
                
                // 设置请求中的所有属性
                for (Map.Entry<String, Object> entry : properties.entrySet()) {
                    node.setProperty(entry.getKey(), entry.getValue());
                }
                
                tx.success();
            }

            Map<String, Object> response = new HashMap<>();
            String baseUrl = "http://localhost:" + ctx.port() + "/db/data/node/" + nodeid;
            
            // 构建响应体
            response.put("extensions", new HashMap<>());
            response.put("labels", baseUrl + "/labels");
            response.put("outgoing_relationships", baseUrl + "/relationships/out");
            response.put("all_typed_relationships", baseUrl + "/relationships/all/{-list|&|types}");
            response.put("traverse", baseUrl + "/traverse/{returnType}");
            response.put("self", baseUrl);
            response.put("property", baseUrl + "/properties/{key}");
            response.put("properties", baseUrl + "/properties");
            response.put("outgoing_typed_relationships", baseUrl + "/relationships/out/{-list|&|types}");
            response.put("incoming_relationships", baseUrl + "/relationships/in");
            response.put("create_relationship", baseUrl + "/relationships");
            response.put("paged_traverse", baseUrl + "/paged/traverse/{returnType}{?pageSize,leaseTime}");
            response.put("all_relationships", baseUrl + "/relationships/all");
            response.put("incoming_typed_relationships", baseUrl + "/relationships/in/{-list|&|types}");

            // 添加元数据
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("id", nodeid);
            metadata.put("labels", Collections.emptyList());
            response.put("metadata", metadata);
            
            // 添加节点属性数据
            response.put("data", properties);

            // 设置响应状态和位置头
            ctx.status(201)
            .header("Location", baseUrl)
            .json(response);
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