package handlers;

import io.javalin.http.Context;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.neo4j.graphdb.DynamicLabel;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Transaction;
import org.neo4j.tooling.GlobalGraphOperations;

import com.google.gson.Gson;

public class LabelHandler {
    private final GraphDatabaseService graphDb;
    
    public LabelHandler(GraphDatabaseService graphDb) {
        this.graphDb = graphDb;
    }

    // 获取具有特定标签的所有节点，支持可选的属性过滤
    public void getNodesWithLabel(Context ctx) {
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
    }

    // 列出所有标签
    public void getAllLabels(Context ctx) {
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
    }
}
