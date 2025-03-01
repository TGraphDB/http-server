package handlers;

import io.javalin.http.Context;
import org.neo4j.graphdb.*;
import org.neo4j.tooling.GlobalGraphOperations;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.util.*;

public class RelationshipHandler {
    private GraphDatabaseService graphDb;
    
    public RelationshipHandler(GraphDatabaseService graphDb) {
        this.graphDb = graphDb;
    }

    // setGraphDb
    public void setGraphDb(GraphDatabaseService graphDb) {
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

    // 获取关系上所有属性API
    public void getProperties(Context ctx) {
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

    // 设置关系上的所有属性
    public void setProperties(Context ctx) {
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
                try {
                    Object propertyValue = convertJsonElementToPropertyValue(entry.getValue());
                    if (propertyValue != null) {
                        relationship.setProperty(entry.getKey(), propertyValue);
                    }
                } catch (IllegalArgumentException e) {
                    // 处理不支持的属性值类型
                    Map<String, Object> errorResponse = new HashMap<>();
                    errorResponse.put("message", String.format("Could not set property \"%s\", %s", 
                        entry.getKey(), e.getMessage()));
                    errorResponse.put("exception", "PropertyValueException");
                    
                    List<Map<String, String>> errors = new ArrayList<>();
                    Map<String, String> error = new HashMap<>();
                    error.put("message", String.format("Could not set property \"%s\", %s",
                        entry.getKey(), e.getMessage()));
                    error.put("code", "Neo.ClientError.Statement.InvalidArguments");
                    errors.add(error);
                    errorResponse.put("errors", errors);
                    
                    ctx.status(400).json(errorResponse);
                    return;
                }
            }
            
            tx.success();
            ctx.status(204);
        }
    }

    // 获取关系上的单个属性
    public void getProperty(Context ctx) {
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
    }

    // 设置关系上的单个属性
    public void setProperty(Context ctx) {
        long relationshipId = Long.parseLong(ctx.pathParam("id"));
        String propertyKey = ctx.pathParam("key");
        JsonElement value = new Gson().fromJson(ctx.body(), JsonElement.class);
        
        try (Transaction tx = graphDb.beginTx()) {
            Relationship relationship = graphDb.getRelationshipById(relationshipId);
            Object propertyValue = convertJsonElementToPropertyValue(value);
                if (propertyValue != null) {
                    relationship.setProperty(propertyKey, propertyValue);
                }
            tx.success();
            ctx.status(204);
        }
    }

    // 获取所有关系（有关系和没有关系的）
    public void getAllRelationships(Context ctx) {
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
    }

    // 获取传入的关系
    public void getIncomingRelationships(Context ctx) {
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
    }

    // 获取传出的关系
    public void getOutgoingRelationships(Context ctx) {
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
    }

    // 获取指定类型的关系 tx.success()是在try里，没有包含在整个transaction里
    public void getRelationshipsByTypes(Context ctx) {
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
    }

    // 获取关系类型
    public void getRelationshipTypes(Context ctx) {
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
    }

    // 从关系中删除所有属性
    public void deleteAllProperties(Context ctx) {
        long relationshipId = Long.parseLong(ctx.pathParam("id"));
        
        try (Transaction tx = graphDb.beginTx()) {
            try {
                Relationship relationship = graphDb.getRelationshipById(relationshipId);
                
                // 移除所有属性
                for (String key : relationship.getPropertyKeys()) {
                    relationship.removeProperty(key);
                }
                
                tx.success();
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
        }
    }

    // 从关系中删除单个属性
    public void deleteProperty(Context ctx) {
        long relationshipId = Long.parseLong(ctx.pathParam("id"));
        String propertyKey = ctx.pathParam("key");
        
        try (Transaction tx = graphDb.beginTx()) {
            try {
                Relationship relationship = graphDb.getRelationshipById(relationshipId);
                
                // 检查属性是否存在
                if (relationship.hasProperty(propertyKey)) {
                    relationship.removeProperty(propertyKey);
                    tx.success();
                    ctx.status(204);
                } else {
                    // 如果属性不存在，返回404并提供详细错误信息
                    Map<String, Object> errorResponse = new HashMap<>();
                    errorResponse.put("message", String.format("Relationship[%d] does not have a property \"%s\"",
                        relationshipId, propertyKey));
                    errorResponse.put("exception", "NoSuchPropertyException");
                    errorResponse.put("fullname", "org.neo4j.server.rest.web.NoSuchPropertyException");
                    
                    // 添加堆栈跟踪
                    List<String> stackTrace = new ArrayList<>();
                    for (StackTraceElement element : Thread.currentThread().getStackTrace()) {
                        stackTrace.add(element.toString());
                    }
                    errorResponse.put("stackTrace", stackTrace);
                    
                    // 添加错误详情
                    List<Map<String, String>> errors = new ArrayList<>();
                    Map<String, String> error = new HashMap<>();
                    error.put("message", String.format("Relationship[%d] does not have a property \"%s\"",
                        relationshipId, propertyKey));
                    error.put("code", "Neo.ClientError.Statement.NoSuchProperty");
                    errors.add(error);
                    errorResponse.put("errors", errors);
                    
                    ctx.status(404).json(errorResponse);
                }
                
            } catch (NotFoundException e) {
                // 如果关系不存在，返回404
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

    // 辅助方法：获取关系的所有属性
    private static Map<String, Object> getRelationshipProperties(Relationship relationship) {
        Map<String, Object> properties = new HashMap<>();
        for (String key : relationship.getPropertyKeys()) {
            properties.put(key, relationship.getProperty(key));
        }
        return properties;
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
}