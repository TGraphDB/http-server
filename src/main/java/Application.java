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
import com.google.gson.JsonElement;
import com.google.gson.Gson;

import org.neo4j.graphdb.Transaction;
import org.neo4j.tooling.GlobalGraphOperations; // 着重了解一下

import service.UserService;
import tgraph.Tgraph;
import model.User;
import util.PasswordUtil;
import config.SecurityConfig;

import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.NotFoundException;
import org.neo4j.graphdb.RelationshipType;


public class Application {
    private static UserService userService = new UserService();
    private static Tgraph tgraph = new Tgraph();
    private static GraphDatabaseService graphDb = tgraph.startDb("target/neo4j-hello-db");
    
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

            long nodeid = 0;

            // 创建节点（并设置属性）
            try (Transaction tx = graphDb.beginTx()) {
                Node node = graphDb.createNode();
                nodeid = node.getId();
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

        // 获取节点API(存在和不存在的)
        app.get("/db/data/node/{id}", ctx -> {
            long nodeId = Long.parseLong(ctx.pathParam("id"));
            
            try (Transaction tx = graphDb.beginTx()) {
                // 通过nodeid属性查找节点
                try {
                    Node node = graphDb.getNodeById(nodeId);
                    Map<String, Object> response = new HashMap<>();
                    String baseUrl = "http://localhost:" + ctx.port() + "/db/data/node/" + nodeId;

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
                    metadata.put("id", nodeId);
                    metadata.put("labels", Collections.emptyList());
                    response.put("metadata", metadata);

                    // 添加节点数据
                    Map<String, Object> data = new HashMap<>();
                    for (String key : node.getPropertyKeys()) {
                        if (!key.equals("nodeid")) { // 排除nodeid属性
                            data.put(key, node.getProperty(key));
                        }
                    }
                    response.put("data", data);

                    ctx.status(200).json(response);

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

                tx.success();
            }
        });

        // 删除节点API
        app.delete("/db/data/node/{id}", ctx -> {
            long nodeId = Long.parseLong(ctx.pathParam("id"));
            
            try (Transaction tx = graphDb.beginTx()) {
                try {
                    Node node = graphDb.getNodeById(nodeId);
                    
                    // 检查节点是否有关系
                    if (node.hasRelationship()) {
                        // 如果有关系，返回409错误
                        Map<String, Object> errorResponse = new HashMap<>();
                        List<Map<String, String>> errors = new ArrayList<>();
                        Map<String, String> error = new HashMap<>();
                        error.put("message", "The node with id " + nodeId + " cannot be deleted. Check that the node is orphaned before deletion.");
                        error.put("code", "Neo.ClientError.Schema.ConstraintViolation");
                        errors.add(error);
                        errorResponse.put("errors", errors);
                        
                        ctx.status(409).json(errorResponse);
                        return;
                    }
                    
                    // 如果没有关系，删除节点
                    node.delete();
                    ctx.status(204);
                    
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
                tx.success();
            }
        });

        // 通过ID获取关系API
        app.get("/db/data/relationship/{id}", ctx -> {
            long relationshipId = Long.parseLong(ctx.pathParam("id"));
            
            try (Transaction tx = graphDb.beginTx()) {
                try {
                    Relationship relationship = graphDb.getRelationshipById(relationshipId);
                    
                    Map<String, Object> response = new HashMap<>();
                    String baseUrl = "http://localhost:" + ctx.port();
                    
                    // 构建响应体
                    response.put("extensions", new HashMap<>());
                    response.put("start", baseUrl + "/db/data/node/" + relationship.getStartNode().getId());
                    response.put("property", baseUrl + "/db/data/relationship/" + relationshipId + "/properties/{key}");
                    response.put("self", baseUrl + "/db/data/relationship/" + relationshipId);
                    response.put("properties", baseUrl + "/db/data/relationship/" + relationshipId + "/properties");
                    response.put("type", relationship.getType().name());
                    response.put("end", baseUrl + "/db/data/node/" + relationship.getEndNode().getId());
                    
                    // 添加元数据
                    Map<String, Object> metadata = new HashMap<>();
                    metadata.put("id", relationshipId);
                    metadata.put("type", relationship.getType().name());
                    response.put("metadata", metadata);
                    
                    // 添加关系属性数据
                    Map<String, Object> data = new HashMap<>();
                    for (String key : relationship.getPropertyKeys()) {
                        data.put(key, relationship.getProperty(key));
                    }
                    response.put("data", data);
                    
                    ctx.status(200).json(response);
                    
                } catch (NotFoundException e) {
                    Map<String, Object> errorResponse = new HashMap<>();
                    List<Map<String, String>> errors = new ArrayList<>();
                    Map<String, String> error = new HashMap<>();
                    error.put("message", "Unable to load RELATIONSHIP with id " + relationshipId + ".");
                    error.put("code", "Neo.ClientError.Statement.EntityNotFound");
                    errors.add(error);
                    errorResponse.put("errors", errors);
                    ctx.status(404).json(errorResponse);
                }
                
                tx.success();
            }
        });

        // 创建关系API
        app.post("/db/data/node/{id}/relationships", ctx -> {
            long startNodeId = Long.parseLong(ctx.pathParam("id"));
            
            // 解析请求体
            JsonObject body = new Gson().fromJson(ctx.body(), JsonObject.class);
            String toNodeUrl = body.get("to").getAsString();
            String relationType = body.get("type").getAsString();
            
            // 从URL中提取目标节点ID
            long endNodeId = extractNodeIdFromUrl(toNodeUrl);
            
            // 获取可选的关系属性
            Map<String, Object> properties = new HashMap<>();
            if (body.has("data")) {
                JsonObject data = body.getAsJsonObject("data");
                for (Map.Entry<String, JsonElement> entry : data.entrySet()) {
                    properties.put(entry.getKey(), entry.getValue().getAsString());
                }
            }
            
            try (Transaction tx = graphDb.beginTx()) {
                try {
                    // 获取起始和结束节点
                    Node startNode = graphDb.getNodeById(startNodeId);
                    Node endNode = graphDb.getNodeById(endNodeId);
                    
                    // 创建关系
                    Relationship relationship = startNode.createRelationshipTo(endNode, new DynamicRelationshipType(relationType));
                    
                    // 设置关系属性
                    for (Map.Entry<String, Object> entry : properties.entrySet()) {
                        relationship.setProperty(entry.getKey(), entry.getValue());
                    }
                    
                    // 构建响应
                    String baseUrl = "http://localhost:" + ctx.port();
                    Map<String, Object> response = new HashMap<>();
                    
                    // 添加基本信息
                    response.put("extensions", new HashMap<>());
                    response.put("start", baseUrl + "/db/data/node/" + startNodeId);
                    response.put("property", baseUrl + "/db/data/relationship/" + relationship.getId() + "/properties/{key}");
                    response.put("self", baseUrl + "/db/data/relationship/" + relationship.getId());
                    response.put("properties", baseUrl + "/db/data/relationship/" + relationship.getId() + "/properties");
                    response.put("type", relationType);
                    response.put("end", baseUrl + "/db/data/node/" + endNodeId);
                    
                    // 添加元数据
                    Map<String, Object> metadata = new HashMap<>();
                    metadata.put("id", relationship.getId());
                    metadata.put("type", relationType);
                    response.put("metadata", metadata);
                    
                    // 添加属性数据
                    response.put("data", properties);
                    
                    // 设置响应状态和位置头
                    ctx.status(201)
                       .header("Location", baseUrl + "/db/data/relationship/" + relationship.getId())
                       .json(response);
                    
                    tx.success();
                    
                } catch (NotFoundException e) {
                    Map<String, Object> errorResponse = new HashMap<>();
                    List<Map<String, String>> errors = new ArrayList<>();
                    Map<String, String> error = new HashMap<>();
                    error.put("message", "One or more specified nodes cannot be found");
                    error.put("code", "Neo.ClientError.Statement.EntityNotFound");
                    errors.add(error);
                    errorResponse.put("errors", errors);
                    ctx.status(404).json(errorResponse);
                }
            }
        });

        // 删除关系API
        app.delete("/db/data/relationship/{id}", ctx -> {
            long relationshipId = Long.parseLong(ctx.pathParam("id"));
            
            try (Transaction tx = graphDb.beginTx()) {
                try {
                    Relationship relationship = graphDb.getRelationshipById(relationshipId);
                    relationship.delete();
                    ctx.status(204);
                } catch (NotFoundException e) {
                    Map<String, Object> errorResponse = new HashMap<>();
                    List<Map<String, String>> errors = new ArrayList<>();
                    Map<String, String> error = new HashMap<>();
                    error.put("message", "Unable to load RELATIONSHIP with id " + relationshipId + ".");
                    error.put("code", "Neo.ClientError.Statement.EntityNotFound");
                    errors.add(error);
                    errorResponse.put("errors", errors);
                    ctx.status(404).json(errorResponse);
                }
                tx.success();
            }
        });

        // 获取关系上所有属性API
        app.get("/db/data/relationship/{id}/properties", ctx -> {
            long relationshipId = Long.parseLong(ctx.pathParam("id"));
            
            try (Transaction tx = graphDb.beginTx()) {
                try {
                    Relationship relationship = graphDb.getRelationshipById(relationshipId);
                    
                    // 构建属性Map
                    Map<String, Object> properties = new HashMap<>();
                    for (String key : relationship.getPropertyKeys()) {
                        properties.put(key, relationship.getProperty(key));
                    }
                    
                    ctx.status(200).json(properties);
                    
                } catch (NotFoundException e) {
                    Map<String, Object> errorResponse = new HashMap<>();
                    List<Map<String, String>> errors = new ArrayList<>();
                    Map<String, String> error = new HashMap<>();
                    error.put("message", "Unable to load RELATIONSHIP with id " + relationshipId + ".");
                    error.put("code", "Neo.ClientError.Statement.EntityNotFound");
                    errors.add(error);
                    errorResponse.put("errors", errors);
                    ctx.status(404).json(errorResponse);
                }
                tx.success();
            }
        });

        // 设置关系上的所有属性 目前仅支持三种类型
        app.put("/db/data/relationship/{id}/properties", ctx -> {
            long relationshipId = Long.parseLong(ctx.pathParam("id"));
            JsonObject properties = new Gson().fromJson(ctx.body(), JsonObject.class);
            
            try (Transaction tx = graphDb.beginTx()) {
                Relationship relationship = graphDb.getRelationshipById(relationshipId);
                
                // 移除所有现有属性
                for (String key : relationship.getPropertyKeys()) {
                    relationship.removeProperty(key);
                }
                
                // 设置新属性
                for (Map.Entry<String, JsonElement> entry : properties.entrySet()) {
                    JsonElement value = entry.getValue();
                    if (value.isJsonPrimitive()) {
                        JsonPrimitive primitive = value.getAsJsonPrimitive();
                        if (primitive.isString()) {
                            relationship.setProperty(entry.getKey(), primitive.getAsString());
                        } else if (primitive.isNumber()) {
                            relationship.setProperty(entry.getKey(), primitive.getAsNumber());
                        } else if (primitive.isBoolean()) {
                            relationship.setProperty(entry.getKey(), primitive.getAsBoolean());
                        }
                    }
                }
                
                tx.success();
                ctx.status(204);
            }
        });

        // 获取关系上的单个属性
        app.get("/db/data/relationship/{id}/properties/{key}", ctx -> {
            long relationshipId = Long.parseLong(ctx.pathParam("id"));
            String propertyKey = ctx.pathParam("key");
            
            try (Transaction tx = graphDb.beginTx()) {
                Relationship relationship = graphDb.getRelationshipById(relationshipId);
                if (relationship.hasProperty(propertyKey)) {
                    Object value = relationship.getProperty(propertyKey);
                    tx.success();
                    ctx.status(200).json(value);
                } 
            }
        });

        // 设置关系上的单个属性
        app.put("/db/data/relationship/{id}/properties/{key}", ctx -> {
            long relationshipId = Long.parseLong(ctx.pathParam("id"));
            String propertyKey = ctx.pathParam("key");
            JsonElement value = new Gson().fromJson(ctx.body(), JsonElement.class);
            
            try (Transaction tx = graphDb.beginTx()) {
                Relationship relationship = graphDb.getRelationshipById(relationshipId);
                if (value.isJsonPrimitive()) {
                    JsonPrimitive primitive = value.getAsJsonPrimitive();
                    if (primitive.isString()) {
                        relationship.setProperty(propertyKey, primitive.getAsString());
                    } else if (primitive.isNumber()) {
                        relationship.setProperty(propertyKey, primitive.getAsNumber());
                    } else if (primitive.isBoolean()) {
                        relationship.setProperty(propertyKey, primitive.getAsBoolean());
                    }
                }
                
                tx.success();
                ctx.status(204);
            }
        });

        // 获取所有关系（有关系和没有关系的）
        app.get("/db/data/node/{id}/relationships/all", ctx -> {
            long nodeId = Long.parseLong(ctx.pathParam("id"));
            String baseUrl = "http://localhost:" + ctx.port();
            
            try (Transaction tx = graphDb.beginTx()) {
                    Node node = graphDb.getNodeById(nodeId);
                    List<Map<String, Object>> relationships = new ArrayList<>();
                    
                    for (Relationship rel : node.getRelationships()) {
                        Map<String, Object> relData = new HashMap<>();
                        
                        // 构建基本信息
                        relData.put("start", baseUrl + "/db/data/node/" + rel.getStartNode().getId());
                        relData.put("data", getRelationshipProperties(rel));
                        relData.put("self", baseUrl + "/db/data/relationship/" + rel.getId());
                        relData.put("property", baseUrl + "/db/data/relationship/" + rel.getId() + "/properties/{key}");
                        relData.put("properties", baseUrl + "/db/data/relationship/" + rel.getId() + "/properties");
                        relData.put("type", rel.getType().name());
                        relData.put("extensions", new HashMap<>());
                        relData.put("end", baseUrl + "/db/data/node/" + rel.getEndNode().getId());
                        
                        // 添加元数据
                        Map<String, Object> metadata = new HashMap<>();
                        metadata.put("id", rel.getId());
                        metadata.put("type", rel.getType().name());
                        relData.put("metadata", metadata);
                        
                        relationships.add(relData);
                    }
                    
                    tx.success();
                    ctx.status(200).json(relationships);
            }
        });

        // 获取传入的关系
        app.get("/db/data/node/{id}/relationships/in", ctx -> {
            long nodeId = Long.parseLong(ctx.pathParam("id"));
            String baseUrl = "http://localhost:" + ctx.port();

            try (Transaction tx = graphDb.beginTx()) {
                try {
                    Node node = graphDb.getNodeById(nodeId);
                    List<Map<String, Object>> relationships = new ArrayList<>();

                    // 遍历传入(in)关系
                    for (Relationship rel : node.getRelationships(Direction.INCOMING)) {
                        Map<String, Object> relData = new HashMap<>();
                        relData.put("start", baseUrl + "/db/data/node/" + rel.getStartNode().getId());
                        relData.put("data", getRelationshipProperties(rel));
                        relData.put("self", baseUrl + "/db/data/relationship/" + rel.getId());
                        relData.put("property", baseUrl + "/db/data/relationship/" + rel.getId() + "/properties/{key}");
                        relData.put("properties", baseUrl + "/db/data/relationship/" + rel.getId() + "/properties");
                        relData.put("type", rel.getType().name());
                        relData.put("extensions", new HashMap<>());
                        relData.put("end", baseUrl + "/db/data/node/" + rel.getEndNode().getId());

                        Map<String, Object> metadata = new HashMap<>();
                        metadata.put("id", rel.getId());
                        metadata.put("type", rel.getType().name());
                        relData.put("metadata", metadata);

                        relationships.add(relData);
                    }

                    tx.success();
                    ctx.status(200).json(relationships);

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

        // 获取传出的关系
        app.get("/db/data/node/{id}/relationships/out", ctx -> {
            long nodeId = Long.parseLong(ctx.pathParam("id"));
            String baseUrl = "http://localhost:" + ctx.port();

            try (Transaction tx = graphDb.beginTx()) {
                try {
                    Node node = graphDb.getNodeById(nodeId);
                    List<Map<String, Object>> relationships = new ArrayList<>();

                    // 遍历传出(out)关系
                    for (Relationship rel : node.getRelationships(Direction.OUTGOING)) {
                        Map<String, Object> relData = new HashMap<>();
                        relData.put("start", baseUrl + "/db/data/node/" + rel.getStartNode().getId());
                        relData.put("data", getRelationshipProperties(rel));
                        relData.put("self", baseUrl + "/db/data/relationship/" + rel.getId());
                        relData.put("property", baseUrl + "/db/data/relationship/" + rel.getId() + "/properties/{key}");
                        relData.put("properties", baseUrl + "/db/data/relationship/" + rel.getId() + "/properties");
                        relData.put("type", rel.getType().name());
                        relData.put("extensions", new HashMap<>());
                        relData.put("end", baseUrl + "/db/data/node/" + rel.getEndNode().getId());

                        Map<String, Object> metadata = new HashMap<>();
                        metadata.put("id", rel.getId());
                        metadata.put("type", rel.getType().name());
                        relData.put("metadata", metadata);

                        relationships.add(relData);
                    }

                    tx.success();
                    ctx.status(200).json(relationships);

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

        // 获取指定类型的关系 tx.success()是在try里，没有包含在整个transaction里
        app.get("/db/data/node/{id}/relationships/all/{typeString}", ctx -> {
            long nodeId = Long.parseLong(ctx.pathParam("id"));
            // 例如 "LIKES&HATES"
            String typesParam = ctx.pathParam("typeString"); 
            String baseUrl = "http://localhost:" + ctx.port();
        
            try (Transaction tx = graphDb.beginTx()) {
                try {
                    Node node = graphDb.getNodeById(nodeId);
                    List<Map<String, Object>> relationships = new ArrayList<>();
        
                    // 将 typesParam 按 '&' 切分并转换成 RelationshipType[]
                    String[] typeNames = typesParam.split("&");
                    RelationshipType[] relationshipTypes = new RelationshipType[typeNames.length];
                    for (int i = 0; i < typeNames.length; i++) {
                        relationshipTypes[i] = new DynamicRelationshipType(typeNames[i]);
                    }
        
                    // 仅获取指定类型的关系
                    for (Relationship rel : node.getRelationships(relationshipTypes)) {
                        Map<String, Object> relData = new HashMap<>();
                        relData.put("start", baseUrl + "/db/data/node/" + rel.getStartNode().getId());
                        relData.put("data", getRelationshipProperties(rel));
                        relData.put("self", baseUrl + "/db/data/relationship/" + rel.getId());
                        relData.put("property", baseUrl + "/db/data/relationship/" + rel.getId() + "/properties/{key}");
                        relData.put("properties", baseUrl + "/db/data/relationship/" + rel.getId() + "/properties");
                        relData.put("type", rel.getType().name());
                        relData.put("extensions", new HashMap<>());
                        relData.put("end", baseUrl + "/db/data/node/" + rel.getEndNode().getId());
        
                        // 添加元数据
                        Map<String, Object> metadata = new HashMap<>();
                        metadata.put("id", rel.getId());
                        metadata.put("type", rel.getType().name());
                        relData.put("metadata", metadata);
        
                        relationships.add(relData);
                    }
        
                    tx.success();
                    ctx.status(200).json(relationships);
        
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

        // 获取关系类型
        app.get("/db/data/relationship/types", ctx -> {
            try (Transaction tx = graphDb.beginTx()) {
                // 使用 GlobalGraphOperations 获取所有关系类型
                GlobalGraphOperations ggo = GlobalGraphOperations.at(graphDb);
                Set<String> typeSet = new HashSet<>();
                for (RelationshipType type : ggo.getAllRelationshipTypes()) {
                    typeSet.add(type.name());
                }
                
                // 转换为列表并排序（可选）
                List<String> types = new ArrayList<>(typeSet);
                Collections.sort(types);
                
                tx.success();
                ctx.status(200).json(types);
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

    // 辅助方法用于从URL中提取节点ID
    private static long extractNodeIdFromUrl(String url) {
        String[] parts = url.split("/");
        return Long.parseLong(parts[parts.length - 1]);
    }

    // 辅助方法：获取关系的所有属性
    private static Map<String, Object> getRelationshipProperties(Relationship relationship) {
        Map<String, Object> properties = new HashMap<>();
        for (String key : relationship.getPropertyKeys()) {
            properties.put(key, relationship.getProperty(key));
        }
        return properties;
    }
}