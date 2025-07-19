package handlers;

import io.javalin.http.Context;
import tgraph.Tgraph;
import util.ServerConfig;
import org.neo4j.graphdb.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.gson.Gson;

public class LabelHandler {
    //private GraphDatabaseService graphDb;
    
    public LabelHandler() {
    }

    // 获取具有特定标签的所有节点，支持可选的属性过滤
    public void getNodesWithLabel(Context ctx) {
        String labelName = ctx.pathParam("labelName");
        String domainName = ServerConfig.getString("org.neo4j.server.domain.name", "localhost");
        String baseUrl = "http://" + domainName + ":" + ctx.port();
        
        try (Transaction tx = Tgraph.graphDb.database("neo4j").beginTx()) {
            try {
                Label label = Label.label(labelName);
                List<Map<String, Object>> nodes = new ArrayList<>();
                
                // 获取所有具有指定标签的节点
                ResourceIterator<Node> labeledNodes = tx.findNodes(label);
                
                // 检查是否有属性查询参数
                Map<String, List<String>> queryParams = ctx.queryParamMap();
                
                while (labeledNodes.hasNext()) {
                    Node node = labeledNodes.next();
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
                
                tx.commit();
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
        // 检查数据库是否已启动
        if (Tgraph.graphDb == null) {
            ctx.status(400).json("Database is not started");
            return;
        }

        try (Transaction tx = Tgraph.graphDb.database("neo4j").beginTx()) {
            Set<String> labels = new HashSet<>();
            
            // 检查是否需要只返回正在使用的标签
            boolean onlyInUse = !("0".equals(ctx.queryParam("in_use")));
            
            if (onlyInUse) {
                // 只获取正在使用的标签
                for (Node node : tx.getAllNodes()) {
                    for (Label label : node.getLabels()) {
                        labels.add(label.name());
                    }
                }
            } else {
                // 获取所有标签（包括未使用的）
                for (Label label : tx.getAllLabels()) {
                    labels.add(label.name());
                }
            }
            
            // 转换为列表并排序
            List<String> sortedLabels = new ArrayList<>(labels);
            Collections.sort(sortedLabels);
            
            tx.commit();
            ctx.status(200).json(sortedLabels);
        }
    }

    // 获取数据库中的节点总数
    public void getNodeCount(Context ctx) {
        try (Transaction tx = Tgraph.graphDb.database("neo4j").beginTx()) {
            long count = 0;
            for (Node node : tx.getAllNodes()) {
                count++;
            }
            ctx.status(200).json(count);
        }
    }

    // 获取数据库中的关系总数
    public void getRelationshipCount(Context ctx) {
        try (Transaction tx = Tgraph.graphDb.database("neo4j").beginTx()) {
            long count = 0;
            for (Relationship relationship : tx.getAllRelationships()) {
                count++;
            }
            ctx.status(200).json(count);
        }
    }

    // 获取所有节点及其详细信息
    public void getAllNodes(Context ctx) {
        String domainName = ServerConfig.getString("org.neo4j.server.domain.name", "localhost");
        String baseUrl = "http://" + domainName + ":" + ctx.port();
        
        try (Transaction tx = Tgraph.graphDb.database("neo4j").beginTx()) {
            List<Map<String, Object>> nodesList = new ArrayList<>();
            
            for (Node node : tx.getAllNodes()) {
                Map<String, Object> nodeData = new HashMap<>();
                long nodeId = node.getId();
                
                // 基本信息
                nodeData.put("id", nodeId);
                nodeData.put("self", baseUrl + "/db/data/node/" + nodeId);
                
                // 获取标签
                List<String> labelNames = new ArrayList<>();
                for (Label label : node.getLabels()) {
                    labelNames.add(label.name());
                }
                nodeData.put("labels", labelNames);
                
                // 获取属性
                Map<String, Object> properties = new HashMap<>();
                for (String key : node.getPropertyKeys()) {
                    properties.put(key, node.getProperty(key));
                }
                nodeData.put("properties", properties);
                
                // 计算度数(关系数量)
                int degree = 0;
                for (Relationship rel : node.getRelationships()) {
                    degree++;
                }
                nodeData.put("degree", degree);
                
                nodesList.add(nodeData);
            }
            
            tx.commit();
            ctx.status(200).json(nodesList);
            
        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            List<Map<String, String>> errors = new ArrayList<>();
            Map<String, String> error = new HashMap<>();
            error.put("message", "Error retrieving nodes: " + e.getMessage());
            error.put("code", "Neo.ClientError.Statement.ExecutionFailed");
            errors.add(error);
            errorResponse.put("errors", errors);
            ctx.status(500).json(errorResponse);
        }
    }


    //获取分页节点信息
    public void getPaginatedNodes(Context ctx) {
        String domainName = ServerConfig.getString("org.neo4j.server.domain.name", "localhost");
        String baseUrl = "http://" + domainName + ":" + ctx.port();
        
        // 获取分页参数
        String pageParam = ctx.queryParam("page");
        String sizeParam = ctx.queryParam("size");
        
        // 参数验证
        if (pageParam == null || sizeParam == null) {
            Map<String, Object> errorResponse = new HashMap<>();
            List<Map<String, String>> errors = new ArrayList<>();
            Map<String, String> error = new HashMap<>();
            error.put("message", "缺少必要的分页参数 page 和 size");
            error.put("code", "Neo.ClientError.Request.InvalidFormat");
            errors.add(error);
            errorResponse.put("errors", errors);
            ctx.status(400).json(errorResponse);
            return;
        }
        
        int page, size;
        try {
            page = Integer.parseInt(pageParam);
            size = Integer.parseInt(sizeParam);
            
            if (page < 1 || size < 1) {
                throw new NumberFormatException("Page and size must be positive integers");
            }
        } catch (NumberFormatException e) {
            Map<String, Object> errorResponse = new HashMap<>();
            List<Map<String, String>> errors = new ArrayList<>();
            Map<String, String> error = new HashMap<>();
            error.put("message", "分页参数格式错误: " + e.getMessage());
            error.put("code", "Neo.ClientError.Request.InvalidFormat");
            errors.add(error);
            errorResponse.put("errors", errors);
            ctx.status(400).json(errorResponse);
            return;
        }
        
        try (Transaction tx = Tgraph.graphDb.database("neo4j").beginTx()) {
            List<Map<String, Object>> allNodes = new ArrayList<>();
            
            // 先将所有节点收集到列表中
            for (Node node : tx.getAllNodes()) {
                Map<String, Object> nodeData = new HashMap<>();
                long nodeId = node.getId();
                
                // 基本信息
                nodeData.put("id", nodeId);
                nodeData.put("self", baseUrl + "/db/data/node/" + nodeId);
                
                // 获取标签
                List<String> labelNames = new ArrayList<>();
                for (Label label : node.getLabels()) {
                    labelNames.add(label.name());
                }
                nodeData.put("labels", labelNames);
                
                // 获取属性
                Map<String, Object> properties = new HashMap<>();
                for (String key : node.getPropertyKeys()) {
                    properties.put(key, node.getProperty(key));
                }
                nodeData.put("properties", properties);
                
                // 计算度数(关系数量)
                int degree = 0;
                for (Relationship rel : node.getRelationships()) {
                    degree++;
                }
                nodeData.put("degree", degree);
                
                allNodes.add(nodeData);
            }
            
            // 计算分页
            int startIndex = (page - 1) * size;
            int endIndex = Math.min(startIndex + size, allNodes.size());
            
            List<Map<String, Object>> paginatedNodes;
            if (startIndex >= allNodes.size()) {
                paginatedNodes = Collections.emptyList(); // 如果起始索引超过总数，返回空列表
            } else {
                paginatedNodes = allNodes.subList(startIndex, endIndex);
            }
            
            tx.commit();
            ctx.status(200).json(paginatedNodes);
            
        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            List<Map<String, String>> errors = new ArrayList<>();
            Map<String, String> error = new HashMap<>();
            error.put("message", "分页获取节点失败: " + e.getMessage());
            error.put("code", "Neo.ClientError.Statement.ExecutionFailed");
            errors.add(error);
            errorResponse.put("errors", errors);
            ctx.status(500).json(errorResponse);
        }
    }

    //分页获取所有关系
    public void getPaginatedRelationships(Context ctx) {
        String domainName = ServerConfig.getString("org.neo4j.server.domain.name", "localhost");
        String baseUrl = "http://" + domainName + ":" + ctx.port();
        
        // 获取分页参数
        String pageParam = ctx.queryParam("page");
        String sizeParam = ctx.queryParam("size");
        
        // 参数验证
        if (pageParam == null || sizeParam == null) {
            Map<String, Object> errorResponse = new HashMap<>();
            List<Map<String, String>> errors = new ArrayList<>();
            Map<String, String> error = new HashMap<>();
            error.put("message", "缺少必要的分页参数 page 和 size");
            error.put("code", "Neo.ClientError.Request.InvalidFormat");
            errors.add(error);
            errorResponse.put("errors", errors);
            ctx.status(400).json(errorResponse);
            return;
        }
        
        int page, size;
        try {
            page = Integer.parseInt(pageParam);
            size = Integer.parseInt(sizeParam);
            
            if (page < 1 || size < 1) {
                throw new NumberFormatException("Page and size must be positive integers");
            }
        } catch (NumberFormatException e) {
            Map<String, Object> errorResponse = new HashMap<>();
            List<Map<String, String>> errors = new ArrayList<>();
            Map<String, String> error = new HashMap<>();
            error.put("message", "分页参数格式错误: " + e.getMessage());
            error.put("code", "Neo.ClientError.Request.InvalidFormat");
            errors.add(error);
            errorResponse.put("errors", errors);
            ctx.status(400).json(errorResponse);
            return;
        }
        
        try (Transaction tx = Tgraph.graphDb.database("neo4j").beginTx()) {
            List<Map<String, Object>> allRelationships = new ArrayList<>();
            
            // 收集所有关系数据
            for (Relationship rel : tx.getAllRelationships()) {
                Map<String, Object> relData = new HashMap<>();
                long relId = rel.getId();
                
                // 基本URL和引用
                relData.put("self", baseUrl + "/db/data/relationship/" + relId);
                relData.put("property", baseUrl + "/db/data/relationship/" + relId + "/properties/{key}");
                relData.put("properties", baseUrl + "/db/data/relationship/" + relId + "/properties");
                relData.put("start", baseUrl + "/db/data/node/" + rel.getStartNode().getId());
                relData.put("end", baseUrl + "/db/data/node/" + rel.getEndNode().getId());
                relData.put("type", rel.getType().name());
                relData.put("extensions", new HashMap<>());
                
                // 关系属性
                Map<String, Object> properties = new HashMap<>();
                for (String key : rel.getPropertyKeys()) {
                    properties.put(key, rel.getProperty(key));
                }
                relData.put("data", properties);
                
                // 元数据
                Map<String, Object> metadata = new HashMap<>();
                metadata.put("id", relId);
                metadata.put("type", rel.getType().name());
                relData.put("metadata", metadata);
                
                allRelationships.add(relData);
            }
            
            // 计算分页
            int startIndex = (page - 1) * size;
            int endIndex = Math.min(startIndex + size, allRelationships.size());
            
            List<Map<String, Object>> paginatedRelationships;
            if (startIndex >= allRelationships.size()) {
                paginatedRelationships = Collections.emptyList(); // 如果起始索引超过总数，返回空列表
            } else {
                paginatedRelationships = allRelationships.subList(startIndex, endIndex);
            }
            
            tx.commit();
            ctx.status(200).json(paginatedRelationships);
            
        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            List<Map<String, String>> errors = new ArrayList<>();
            Map<String, String> error = new HashMap<>();
            error.put("message", "分页获取关系失败: " + e.getMessage());
            error.put("code", "Neo.ClientError.Statement.ExecutionFailed");
            errors.add(error);
            errorResponse.put("errors", errors);
            ctx.status(500).json(errorResponse);
        }
    }
}
