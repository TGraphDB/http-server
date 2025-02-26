import io.javalin.Javalin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import handlers.NodeHandler;
import handlers.RelationshipHandler;

import com.google.gson.JsonElement;
import com.google.gson.Gson;
import com.google.gson.JsonArray;

import org.neo4j.graphdb.Transaction;
import org.neo4j.tooling.GlobalGraphOperations; // 着重了解一下

import service.UserService;
import tgraph.Tgraph;
import service.User;
import util.PasswordUtil;
import service.SecurityConfig;

import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.DynamicLabel;
import org.neo4j.graphdb.Label; // label和dynamiclabel的区别
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.NotFoundException;
import org.neo4j.graphdb.RelationshipType;


public class Application {
    private static UserService userService = new UserService();
    private static Tgraph tgraph = new Tgraph();
    /*
    1. 当应用程序启动时，Application 类被加载
    2. 类加载时会执行静态字段的初始化
    3. tgraph.startDb() 被调用，启动数据库
    4. 数据库实例被赋值给 graphDb 静态字段
     */
    private static GraphDatabaseService graphDb = tgraph.startDb("target/neo4j-hello-db");

     // 创建处理器实例
     private static RelationshipHandler relationshipHandler = new RelationshipHandler(graphDb);
     private static NodeHandler nodeHandler = new NodeHandler(graphDb);
    
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
        app.get("/db/data/propertykeys", ctx -> {
            try (Transaction tx = graphDb.beginTx()) {
                Set<String> propertyKeys = new HashSet<>();
                GlobalGraphOperations ggo = GlobalGraphOperations.at(graphDb);
                
                // 收集节点的属性键
                for (Node node : ggo.getAllNodes()) {
                    for (String key : node.getPropertyKeys()) {
                        propertyKeys.add(key);
                    }
                }
                
                // 收集关系的属性键
                for (Relationship rel : ggo.getAllRelationships()) {
                    for (String key : rel.getPropertyKeys()) {
                        propertyKeys.add(key);
                    }
                }
                
                // 转换为列表并排序
                List<String> keys = new ArrayList<>(propertyKeys);
                Collections.sort(keys);
                
                tx.success();
                ctx.status(200).json(keys);
            }
        });
        
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
        app.get("/db/data/label/{labelName}/nodes", ctx -> {
            String labelName = ctx.pathParam("labelName");
            String baseUrl = "http://localhost:" + ctx.port();
            
            try (Transaction tx = graphDb.beginTx()) {
                try {
                    Label label = DynamicLabel.label(labelName);
                    List<Map<String, Object>> nodes = new ArrayList<>();
                    
                    // 获取所有具有指定标签的节点
                    GlobalGraphOperations ggo = GlobalGraphOperations.at(graphDb);
                    Iterable<Node> labeledNodes = ggo.getAllNodesWithLabel(label);
                    
                    // 检查是否有属性查询参数
                    Map<String, List<String>> queryParams = ctx.queryParamMap();
                    
                    for (Node node : labeledNodes) {
                        boolean includeNode = true;
                        
                        // 如果有查询参数，检查属性值是否匹配
                        if (!queryParams.isEmpty()) {
                            Map.Entry<String, List<String>> entry = queryParams.entrySet().iterator().next();
                            String propertyKey = entry.getKey();
                            String encodedValue = entry.getValue().get(0);
                            String propertyValue = new Gson().fromJson(encodedValue, String.class);
                            
                            includeNode = node.hasProperty(propertyKey) && 
                                        node.getProperty(propertyKey).toString().equals(propertyValue);
                        }
                        
                        if (includeNode) {
                            Map<String, Object> nodeData = new HashMap<>();
                            String nodeUrl = baseUrl + "/db/data/node/" + node.getId();
                            
                            // 添加基本 URL
                            nodeData.put("labels", nodeUrl + "/labels");
                            nodeData.put("outgoing_relationships", nodeUrl + "/relationships/out");
                            nodeData.put("all_typed_relationships", nodeUrl + "/relationships/all/{-list|&|types}");
                            nodeData.put("traverse", nodeUrl + "/traverse/{returnType}");
                            nodeData.put("self", nodeUrl);
                            nodeData.put("property", nodeUrl + "/properties/{key}");
                            nodeData.put("properties", nodeUrl + "/properties");
                            nodeData.put("outgoing_typed_relationships", nodeUrl + "/relationships/out/{-list|&|types}");
                            nodeData.put("incoming_relationships", nodeUrl + "/relationships/in");
                            nodeData.put("extensions", new HashMap<>());
                            nodeData.put("create_relationship", nodeUrl + "/relationships");
                            nodeData.put("paged_traverse", nodeUrl + "/paged/traverse/{returnType}{?pageSize,leaseTime}");
                            nodeData.put("all_relationships", nodeUrl + "/relationships/all");
                            nodeData.put("incoming_typed_relationships", nodeUrl + "/relationships/in/{-list|&|types}");
                            
                            // 添加节点属性
                            Map<String, Object> data = new HashMap<>();
                            for (String key : node.getPropertyKeys()) {
                                data.put(key, node.getProperty(key));
                            }
                            nodeData.put("data", data);
                            
                            // 添加元数据
                            Map<String, Object> metadata = new HashMap<>();
                            metadata.put("id", node.getId());
                            List<String> labels = new ArrayList<>();
                            for (Label l : node.getLabels()) {
                                labels.add(l.name());
                            }
                            metadata.put("labels", labels);
                            nodeData.put("metadata", metadata);
                            
                            nodes.add(nodeData);
                        }
                    }
                    
                    tx.success();
                    ctx.status(200).json(nodes);
                    
                } catch (Exception e) {
                    Map<String, Object> errorResponse = new HashMap<>();
                    List<Map<String, String>> errors = new ArrayList<>();
                    Map<String, String> error = new HashMap<>();
                    error.put("message", "Error retrieving nodes: " + e.getMessage());
                    error.put("code", "Neo.ClientError.Statement.InvalidSyntax");
                    errors.add(error);
                    errorResponse.put("errors", errors);
                    ctx.status(400).json(errorResponse);
                }
            }
        });

        // 列出所有标签
        app.get("/db/data/labels", ctx -> {
            try (Transaction tx = graphDb.beginTx()) {
                Set<String> labels = new HashSet<>();
                GlobalGraphOperations ggo = GlobalGraphOperations.at(graphDb);
                
                // 检查是否需要只返回正在使用的标签
                boolean onlyInUse = !("0".equals(ctx.queryParam("in_use")));
                
                if (onlyInUse) {
                    // 只获取正在使用的标签
                    for (Node node : ggo.getAllNodes()) {
                        for (Label label : node.getLabels()) {
                            labels.add(label.name());
                        }
                    }
                } else {
                    // 获取所有标签（包括未使用的）
                    for (Label label : ggo.getAllLabels()) {
                        labels.add(label.name());
                    }
                }
                
                // 转换为列表并排序
                List<String> sortedLabels = new ArrayList<>(labels);
                Collections.sort(sortedLabels);
                
                tx.success();
                ctx.status(200).json(sortedLabels);
            }
        });

        // 获取节点的度数（各种场景）
        app.get("/db/data/node/{id}/degree/*", ctx -> {
            long nodeId = Long.parseLong(ctx.pathParam("id"));
            String[] pathParts = ctx.path().split("/");
            // 获取最后两个部分，用于确定方向和关系类型
            String direction = pathParts[pathParts.length - 1];
            String types = null;
            
            // 如果路径包含关系类型，则分离方向和类型
            if (direction.contains("/")) {
                String[] dirAndTypes = direction.split("/");
                direction = dirAndTypes[0];
                types = dirAndTypes[1];
            }
            
            try (Transaction tx = graphDb.beginTx()) {
                try {
                    Node node = graphDb.getNodeById(nodeId);
                    int degree = 0;
                    
                    // 根据不同场景计算度数
                    if (types != null) {
                        // 处理特定类型的关系
                        String[] relationshipTypes = types.split("&");
                        RelationshipType[] relTypes = new RelationshipType[relationshipTypes.length];
                        for (int i = 0; i < relationshipTypes.length; i++) {
                            relTypes[i] = new DynamicRelationshipType(relationshipTypes[i]);
                        }
                        
                        // 根据方向计算度数
                        switch (direction.toLowerCase()) {
                            case "in":
                                degree = node.getDegree(Direction.INCOMING);
                                break;
                            case "out":
                                degree = node.getDegree(Direction.OUTGOING);
                                break;
                            default: // "all"
                                degree = node.getDegree();
                        }
                    } else {
                        // 仅根据方向计算度数
                        switch (direction.toLowerCase()) {
                            case "in":
                                degree = node.getDegree(Direction.INCOMING);
                                break;
                            case "out":
                                degree = node.getDegree(Direction.OUTGOING);
                                break;
                            default: // "all"
                                degree = node.getDegree();
                        }
                    }
                    
                    tx.success();
                    ctx.status(200).json(degree);
                    
                } catch (NotFoundException e) {
                    Map<String, Object> errorResponse = new HashMap<>();
                    List<Map<String, String>> errors = new ArrayList<>();
                    Map<String, String> error = new HashMap<>();
                    error.put("message", "Unable to load NODE with id " + nodeId + ".");
                    error.put("code", "Neo.ClientError.Statement.EntityNotFound");
                    errors.add(error);
                    errorResponse.put("errors", errors);
                    ctx.status(404).json(errorResponse);
                }
            }
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

    // Dynamic RelationshipType implementation
    private static class DynamicRelationshipType implements RelationshipType {
        private final String name;
        
        public DynamicRelationshipType(String name) {
            this.name = name;
        }
        
        @Override
        public String name() {
            return name;
        }
    }

    

    // 辅助方法：获取关系的所有属性
    private static Map<String, Object> getRelationshipProperties(Relationship relationship) {
        Map<String, Object> properties = new HashMap<>();
        for (String key : relationship.getPropertyKeys()) {
            properties.put(key, relationship.getProperty(key));
        }
        return properties;
    }

    /**
     * 将JSON元素转换为Neo4j支持的属性值类型
     * 支持:
     * - 字符串
     * - 数字(Long/Double)
     * - 布尔值
     * - 以上类型的数组
     */
    private static Object convertJsonElementToPropertyValue(JsonElement element) {
        // 检查 null 值
        if (element == null || element.isJsonNull()) {
            throw new IllegalArgumentException("Property values cannot be null");
        }
        // 处理基本类型
        if (element.isJsonPrimitive()) {
            JsonPrimitive primitive = element.getAsJsonPrimitive();
            if (primitive.isString()) {
                return primitive.getAsString();
            } else if (primitive.isNumber()) {
                String numStr = primitive.getAsString();
                return numStr.indexOf('.') == -1 ? 
                    primitive.getAsLong() : 
                    primitive.getAsDouble();
            } else if (primitive.isBoolean()) {
                return primitive.getAsBoolean();
            }
        }
        
        // 处理数组类型
        if (element.isJsonArray()) {
            JsonArray array = element.getAsJsonArray();
            if (array.size() > 0) {
                JsonElement first = array.get(0);
                if (first.isJsonPrimitive()) {
                    JsonPrimitive primitive = first.getAsJsonPrimitive();
                    // 字符串数组
                    if (primitive.isString()) {
                        String[] result = new String[array.size()];
                        for (int i = 0; i < array.size(); i++) {
                            result[i] = array.get(i).getAsString();
                        }
                        return result;
                    }
                    // 数字数组
                    else if (primitive.isNumber()) {
                        if (primitive.getAsString().indexOf('.') == -1) {
                            long[] result = new long[array.size()];
                            for (int i = 0; i < array.size(); i++) {
                                result[i] = array.get(i).getAsLong();
                            }
                            return result;
                        } else {
                            double[] result = new double[array.size()];
                            for (int i = 0; i < array.size(); i++) {
                                result[i] = array.get(i).getAsDouble();
                            }
                            return result;
                        }
                    }
                    // 布尔数组
                    else if (primitive.isBoolean()) {
                        boolean[] result = new boolean[array.size()];
                        for (int i = 0; i < array.size(); i++) {
                            result[i] = array.get(i).getAsBoolean();
                        }
                        return result;
                    }
                }
            }
        }
        // 检查嵌套对象
        if (element.isJsonObject()) {
            throw new IllegalArgumentException("Nested objects are not supported as property values");
        }
        
        throw new IllegalArgumentException("Unsupported property value type");
    }

    // 辅助方法：添加单个标签并验证标签名称
    private static void addLabel(Node node, String labelName) {
        if (labelName == null || labelName.trim().isEmpty()) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("message", "Unable to add label, see nested exception.");
            errorResponse.put("exception", "BadInputException");
            errorResponse.put("fullname", "org.neo4j.server.rest.repr.BadInputException");
            throw new IllegalArgumentException(new Gson().toJson(errorResponse));
        }
        
        // 添加有效的标签
        node.addLabel(DynamicLabel.label(labelName));
    }
}