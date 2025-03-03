package handlers;

import io.javalin.http.Context;
import tgraph.Tgraph;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.DynamicLabel;
import org.neo4j.graphdb.DynamicRelationshipType;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.NotFoundException;
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.graphdb.Transaction;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

public class NodeHandler {
    // private GraphDatabaseService graphDb;
    
    public NodeHandler() {
    }

    
    // 创建节点API
    public void createNode(Context ctx) {
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
        try (Transaction tx = Tgraph.graphDb.beginTx()) {
            Node node = Tgraph.graphDb.createNode();
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
    }

    // 获取节点API(存在和不存在的)
    public void getNode(Context ctx) {
        long nodeId = Long.parseLong(ctx.pathParam("id"));
        
        try (Transaction tx = Tgraph.graphDb.beginTx()) {
            // 通过nodeid属性查找节点
            try {
                Node node = Tgraph.graphDb.getNodeById(nodeId);
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
                    data.put(key, node.getProperty(key));
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
    }

    // 删除节点API
    public void deleteNode(Context ctx) {
        long nodeId = Long.parseLong(ctx.pathParam("id"));
        
        try (Transaction tx = Tgraph.graphDb.beginTx()) {
            try {
                Node node = Tgraph.graphDb.getNodeById(nodeId);
                
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
    }

    // 在节点上设置单个属性
    public void setProperty(Context ctx) {
        long nodeId = Long.parseLong(ctx.pathParam("id"));
        String propertyKey = ctx.pathParam("key");
        JsonElement value = new Gson().fromJson(ctx.body(), JsonElement.class);
        
        try (Transaction tx = Tgraph.graphDb.beginTx()) {
            try {
                Node node = Tgraph.graphDb.getNodeById(nodeId);
                
                // 使用通用转换方法处理属性值
                Object propertyValue = convertJsonElementToPropertyValue(value);
                if (propertyValue != null) {
                    node.setProperty(propertyKey, propertyValue);
                }
                
                tx.success();
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
        }
    }

    // 更新节点的所有属性
    public void updateAllProperties(Context ctx) {
        long nodeId = Long.parseLong(ctx.pathParam("id"));
        JsonObject properties = new Gson().fromJson(ctx.body(), JsonObject.class);
        
        try (Transaction tx = Tgraph.graphDb.beginTx()) {
            try {
                Node node = Tgraph.graphDb.getNodeById(nodeId);
                
                // 移除所有现有属性
                for (String key : node.getPropertyKeys()) {
                    node.removeProperty(key);
                }
                
                // 设置新属性
                for (Map.Entry<String, JsonElement> entry : properties.entrySet()) {
                    Object propertyValue = convertJsonElementToPropertyValue(entry.getValue());
                    if (propertyValue != null) {
                        node.setProperty(entry.getKey(), propertyValue);
                    }
                }
                
                tx.success();
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
        }
    }

    // 获取节点的所有属性
    public void getAllProperties(Context ctx) {
        long nodeId = Long.parseLong(ctx.pathParam("id"));
        
        try (Transaction tx = Tgraph.graphDb.beginTx()) {
            try {
                Node node = Tgraph.graphDb.getNodeById(nodeId);
                
                // 构建属性Map
                Map<String, Object> properties = new HashMap<>();
                for (String key : node.getPropertyKeys()) {
                    properties.put(key, node.getProperty(key));
                }
                
                tx.success();
                ctx.status(200).json(properties);
                
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

    // 获取节点的单个属性
    public void getProperty(Context ctx) {
        long nodeId = Long.parseLong(ctx.pathParam("id"));
        String propertyKey = ctx.pathParam("key");
        
        try (Transaction tx = Tgraph.graphDb.beginTx()) {
            try {
                Node node = Tgraph.graphDb.getNodeById(nodeId);
                
                // 检查属性是否存在
                if (node.hasProperty(propertyKey)) {
                    Object value = node.getProperty(propertyKey);
                    tx.success();
                    ctx.status(200).json(value);
                } else {
                    Map<String, Object> errorResponse = new HashMap<>();
                    List<Map<String, String>> errors = new ArrayList<>();
                    Map<String, String> error = new HashMap<>();
                    error.put("message", String.format("Property [%s] not found for Node[%d]", propertyKey, nodeId));
                    error.put("code", "Neo.ClientError.Statement.EntityNotFound");
                    errors.add(error);
                    errorResponse.put("errors", errors);
                    ctx.status(404).json(errorResponse);
                }
                
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

    // 删除节点的所有属性
    public void deleteAllProperties(Context ctx) {
        long nodeId = Long.parseLong(ctx.pathParam("id"));
        
        try (Transaction tx = Tgraph.graphDb.beginTx()) {
            try {
                Node node = Tgraph.graphDb.getNodeById(nodeId);
                
                // 移除所有属性
                for (String key : node.getPropertyKeys()) {
                    node.removeProperty(key);
                }
                
                tx.success();
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
        }
    }

    // 删除节点的单个属性
    public void deleteProperty(Context ctx) {
        long nodeId = Long.parseLong(ctx.pathParam("id"));
        String propertyKey = ctx.pathParam("key");
        
        try (Transaction tx = Tgraph.graphDb.beginTx()) {
            try {
                Node node = Tgraph.graphDb.getNodeById(nodeId);
                
                // 检查属性是否存在
                if (node.hasProperty(propertyKey)) {
                    node.removeProperty(propertyKey);
                    tx.success();
                    ctx.status(204);
                } else {
                    // 如果属性不存在，返回 404
                    Map<String, Object> errorResponse = new HashMap<>();
                    List<Map<String, String>> errors = new ArrayList<>();
                    Map<String, String> error = new HashMap<>();
                    error.put("message", String.format("Property [%s] not found for Node[%d]", propertyKey, nodeId));
                    error.put("code", "Neo.ClientError.Statement.EntityNotFound");
                    errors.add(error);
                    errorResponse.put("errors", errors);
                    ctx.status(404).json(errorResponse);
                }
                
            } catch (NotFoundException e) {
                // 如果节点不存在，返回 404
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


    // 向节点添加标签
    public void addLabels(Context ctx) {
        long nodeId = Long.parseLong(ctx.pathParam("id"));
        JsonElement labelElement = new Gson().fromJson(ctx.body(), JsonElement.class);
        
        try (Transaction tx = Tgraph.graphDb.beginTx()) {
            try {
                Node node = Tgraph.graphDb.getNodeById(nodeId);
                
                if (labelElement.isJsonArray()) {
                    // 处理多个标签
                    JsonArray labels = labelElement.getAsJsonArray();
                    for (JsonElement label : labels) {
                        addLabel(node, label.getAsString());
                    }
                } else {
                    // 处理单个标签
                    String labelName = labelElement.getAsString();
                    addLabel(node, labelName);
                }
                
                tx.success();
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
        }
    }

    // 替换节点上的所有标签
    public void replaceLabels(Context ctx) {
        long nodeId = Long.parseLong(ctx.pathParam("id"));
        JsonElement labelElement = new Gson().fromJson(ctx.body(), JsonElement.class);
        
        try (Transaction tx = Tgraph.graphDb.beginTx()) {
            try {
                Node node = Tgraph.graphDb.getNodeById(nodeId);
                
                // 移除所有现有标签 （有可能没有label，要注意）
                for (Label label : node.getLabels()) {
                    node.removeLabel(label);
                }
                
                // 确保请求体是一个数组
                if (!labelElement.isJsonArray()) {
                    Map<String, Object> errorResponse = new HashMap<>();
                    errorResponse.put("message", "Labels must be supplied as an array");
                    errorResponse.put("exception", "BadInputException");
                    errorResponse.put("fullname", "org.neo4j.server.rest.repr.BadInputException");
                    
                    List<Map<String, String>> errors = new ArrayList<>();
                    Map<String, String> error = new HashMap<>();
                    error.put("message", "Labels must be supplied as an array");
                    error.put("code", "Neo.ClientError.Request.InvalidFormat");
                    errors.add(error);
                    errorResponse.put("errors", errors);
                    
                    ctx.status(400).json(errorResponse);
                    return;
                }
                
                // 添加新标签
                JsonArray labels = labelElement.getAsJsonArray();
                for (JsonElement label : labels) {
                    addLabel(node, label.getAsString());
                }
                
                tx.success();
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
        }
    }

    // 从节点中删除标签
    public void removeLabel(Context ctx) {
        long nodeId = Long.parseLong(ctx.pathParam("id"));
        String labelName = ctx.pathParam("labelName");
        
        try (Transaction tx = Tgraph.graphDb.beginTx()) {
            try {
                Node node = Tgraph.graphDb.getNodeById(nodeId);
                
                // 创建标签对象
                Label label = DynamicLabel.label(labelName);
                
                // 移除标签 (无论标签是否存在)
                node.removeLabel(label);
                
                tx.success();
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
        }
    }

    // 获取节点的所有标签
    public void getAllLabels(Context ctx) {
        long nodeId = Long.parseLong(ctx.pathParam("id"));
        
        try (Transaction tx = Tgraph.graphDb.beginTx()) {
            try {
                Node node = Tgraph.graphDb.getNodeById(nodeId);
                
                // 收集节点的所有标签
                List<String> labels = new ArrayList<>();
                for (Label label : node.getLabels()) {
                    labels.add(label.name());
                }
                
                // 对标签列表进行排序
                Collections.sort(labels);
                
                tx.success();
                ctx.status(200).json(labels);
                
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

    // 获取节点的度数（各种场景）
    public void getDegree(Context ctx) {
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
        
        try (Transaction tx = Tgraph.graphDb.beginTx()) {
            try {
                Node node = Tgraph.graphDb.getNodeById(nodeId);
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
