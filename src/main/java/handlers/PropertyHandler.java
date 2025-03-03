package handlers;

import io.javalin.http.Context;
import tgraph.Tgraph;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.Transaction;
import org.neo4j.tooling.GlobalGraphOperations;

public class PropertyHandler {
    // private GraphDatabaseService graphDb;
    
    public PropertyHandler() {
    }

    // 列出所有属性键API
    public void getAllPropertyKeys(Context ctx) {
        try (Transaction tx = Tgraph.graphDb.beginTx()) {
            Set<String> propertyKeys = new HashSet<>();
            GlobalGraphOperations ggo = GlobalGraphOperations.at(Tgraph.graphDb);
            
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
    }
}
