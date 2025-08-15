package handlers;

import com.alibaba.fastjson.JSON;
import edu.buaa.common.transaction.*;
import edu.buaa.common.utils.PFieldList;
import io.javalin.http.Context;
import org.neo4j.graphdb.Entity;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.temporal.TimePoint;
import tgraph.Tgraph;

import java.util.*;

public class PropertyHandler {
    // private GraphDatabaseService graphDb;
    
    public PropertyHandler() {
    }

    // 列出所有属性键API
    public void getAllPropertyKeys(Context ctx) {
        try (Transaction tx = Tgraph.graphDb.database("neo4j").beginTx()) {
            Set<String> propertyKeys = new HashSet<>();
            
            // 收集节点的属性键
            for (Node node : tx.getAllNodes()) {
                for (String key : node.getPropertyKeys()) {
                    propertyKeys.add(key);
                }
            }
            
            // 收集关系的属性键
            for (Relationship rel : tx.getAllRelationships()) {
                for (String key : rel.getPropertyKeys()) {
                    propertyKeys.add(key);
                }
            }
            
            // 转换为列表并排序
            List<String> keys = new ArrayList<>(propertyKeys);
            Collections.sort(keys);
            
            tx.commit();
            ctx.status(200).json(keys);
        }
    }

    public static final Label LABEL = Label.label("NODE_TYPE");
    public static final RelationshipType RELATIONSHIP_TYPE = RelationshipType.withName("REL_TYPE");


    public Context batchExecuteTransaction(Context ctx) {
        AbstractTransaction tx = JSON.parseObject(ctx.body(), AbstractTransaction.class);
        try {
            switch (tx.getTxType()) {
                case tx_import_static_data:
                    execute((ImportStaticDataTx) tx);
                    return ctx.status(200).json(Collections.emptyMap());
                case tx_import_temporal_data:
                    execute((ImportTemporalDataTx) tx);
                    return ctx.status(200).json(Collections.emptyMap());
                case tx_update_temporal_data:
                    execute((UpdateTemporalDataTx) tx);
                    return ctx.status(200).json(Collections.emptyMap());
                default:
                    throw new UnsupportedOperationException();
            }
        } catch (TransactionFailedException e) {
            Map<String, String> error = new HashMap<>();
            error.put("message", e.getMessage());
            error.put("code", "Neo.ClientError.Statement.EntityNotFound");
            return ctx.status(500).json(error);
        } catch (Throwable e) {
            Map<String, String> error = new HashMap<>();
            error.put("message", e.getMessage());
            error.put("code", "Neo.ClientError.Statement.EntityNotFound");
            return ctx.status(400).json(error);
        }
    }

    private void execute(ImportStaticDataTx tx) {
        GraphDatabaseService db = Tgraph.graphDb.database("neo4j");
        PFieldList nodesData = tx.getNodes();
        Set<String> props = nodesData.keysWithout("u_sid");
        int nSize = nodesData.size();
        try (Transaction transaction = db.beginTx()) {
            for (int i = 0; i < nSize; i++) {
                Node node = transaction.createNode(LABEL);
                String id = nodesData.get("u_sid", i).s();
                node.setProperty("u_sid", id);
                for (String key : props) {
                    node.setProperty(key, nodesData.get(key, i).getVal());
                }
            }
            transaction.commit();
        }
        PFieldList relData = tx.getRels();
        props = relData.keysWithout("u_sid", "r_from", "r_to");
        int rSize = relData.size();
        try (Transaction transaction = db.beginTx()) {
            for (int i = 0; i < rSize; i++) {
                String fromId = relData.get("r_from", i).s();
                String toId = relData.get("r_to", i).s();
                String id = relData.get("u_sid", i).s();
                Node fromNode = transaction.findNode(LABEL, "u_sid", fromId);
                Node toNode= transaction.findNode(LABEL, "u_sid", toId);
                Relationship relationship = fromNode.createRelationshipTo(toNode, RELATIONSHIP_TYPE);
                relationship.setProperty("u_sid", id);
                for (String key : props) {
                    relationship.setProperty(key, relData.get(key, i).getVal());
                }
            }
            transaction.commit();
        }
    }

    protected void execute(ImportTemporalDataTx tx) {
        GraphDatabaseService db = Tgraph.graphDb.database("neo4j");
        try(Transaction transaction = db.beginTx()) {
            PFieldList data = tx.getData();
            Set<String> props = data.keysWithout("u_sid", "t");
            int tSize = data.size();
            for (int i=0; i<tSize; i++) {
                try {
                    String id = data.get("u_sid", i).s();
                    TimePoint time = time(data.get("t", i).i());
                    Entity entity = tx.isNode() ? transaction.findNode(LABEL, "u_sid", id) :
                            transaction.findRelationship(RELATIONSHIP_TYPE, "u_sid", id);
                    for (String prop : props) {
                        entity.setTemporalProperty(prop, time, data.get(prop, i).getVal());
                    }
                }
                catch (IllegalStateException e) {
                    if (!e.getMessage().contains("not found")) throw e;
                }
            }
            transaction.commit();
        }
    }

    protected void execute(UpdateTemporalDataTx tx) {
        GraphDatabaseService db = Tgraph.graphDb.database("neo4j");
        try(Transaction transaction = db.beginTx()) {
            PFieldList data = tx.getData();
            Set<String> props = data.keysWithout("u_sid", "t");
            int tSize = data.size();
            for (int i=0; i<tSize; i++) {
                try {
                    String id = data.get("u_sid", i).s();
                    TimePoint start = time(data.get("st", i).i());
                    TimePoint end = time(data.get("et", i).i());
                    Entity entity = tx.isNode() ? transaction.findNode(LABEL, "u_sid", id) :
                            transaction.findRelationship(RELATIONSHIP_TYPE, "u_sid", id);
                    for (String prop : props) {
                        entity.setTemporalProperty(prop, start, end, data.get(prop, i).getVal());
                    }
                }
                catch (IllegalStateException e) {
                    if (!e.getMessage().contains("not found")) throw e;
                }
            }
            transaction.commit();
        }
    }

    TimePoint time(int t){
        return new TimePoint(t);
    }
}
