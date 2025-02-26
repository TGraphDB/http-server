package handlers;

import io.javalin.http.Context;
import org.neo4j.graphdb.*;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.*;

public class RelationshipHandler {
    private final GraphDatabaseService graphDb;
    
    public RelationshipHandler(GraphDatabaseService graphDb) {
        this.graphDb = graphDb;
    }
    
    public void getRelationship(Context ctx) {
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
    }
    
    public void createRelationship(Context ctx) {
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
    }

    public void deleteRelationship(Context ctx) {
        long relationshipId = Long.parseLong(ctx.pathParam("id"));
        
        try (Transaction tx = graphDb.beginTx()) {
            try {
                Relationship relationship = graphDb.getRelationshipById(relationshipId);
                relationship.delete();
                ctx.status(204);
                tx.success();
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
        }
    }



    // 辅助方法用于从URL中提取节点ID
    private static long extractNodeIdFromUrl(String url) {
        String[] parts = url.split("/");
        return Long.parseLong(parts[parts.length - 1]);
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
}