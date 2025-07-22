package app;

import org.neo4j.dbms.api.DatabaseManagementService;
import org.neo4j.dbms.api.DatabaseManagementServiceBuilder;
import org.neo4j.graphdb.*;
import org.neo4j.io.fs.FileUtils;
import org.neo4j.graphdb.temporal.TimePoint;
import javafx.util.Pair;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class TestTemporalProperty {
    private static final String DB_PATH = "target/tgraph/tgraph-db-test";

    public static DatabaseManagementService graphDb;

    private static Map<Integer, Integer> nodeIdToCntMap = new HashMap<>();
    private static Map<Pair<Integer, Integer>, Integer> roadGridIndexToCntMap = new HashMap<>();

    public static File NodeFile = new File( "target\\data\\node.csv" );
    public static File EdgeFile = new File( "target\\data\\edge.csv" );
    public static File TemporalFile = new File( "target\\data\\100501_100.csv" );

    public static void main( final String[] args ) throws IOException
    {
        FileUtils.deleteDirectory(new File(DB_PATH).toPath());
        graphDb = new DatabaseManagementServiceBuilder(new File( DB_PATH ).toPath()).build();

        // 导入节点
        try ( Transaction tx = graphDb.database("neo4j").beginTx() ){
            try (BufferedReader br = new BufferedReader(new FileReader(NodeFile))) {
                String line;
                boolean isFirstLine = true;
                int cnt = 0;
                // 跳过文件的第一行，第一行是字段名
                while ((line = br.readLine()) != null) {
                    if (isFirstLine) {
                        isFirstLine = false;
                        continue;  // 跳过表头
                    }

                    String[] tokens = line.split(",");

                    // 提取基础的道路链信息
                    int NodeId = Integer.parseInt(tokens[0]);
                    nodeIdToCntMap.put(NodeId, cnt);
                    cnt++;
                    // System.out.println("NodeId: " + NodeId);
                    tx.createNode().setProperty("id", NodeId);
                }
            }
            System.out.println("successfully imported nodes");
            tx.commit();
        }

        // 导入边
        try ( Transaction tx = graphDb.database("neo4j").beginTx() ){
            try (BufferedReader br = new BufferedReader(new FileReader(EdgeFile))) {
                String line;
                boolean isFirstLine = true;
                int cnt = 0;
                // 跳过文件的第一行，第一行是字段名
                while ((line = br.readLine()) != null) {
                    if (isFirstLine) {
                        isFirstLine = false;
                        continue;  // 跳过表头
                    }

                    String[] tokens = line.split(",");

                    // 提取基础的道路链信息
                    int startNodeId = Integer.parseInt(tokens[0]);
                    int endNodeId = Integer.parseInt(tokens[1]);
                    String relationType = tokens[2];
                    int roarUid = Integer.parseInt(tokens[3]);
                    int roadGrid = Integer.parseInt(tokens[4]);
                    int roadIndex = Integer.parseInt(tokens[5]);
                    roadGridIndexToCntMap.put(new Pair<>(roadGrid, roadIndex), cnt);

                    Node startNode = tx.getNodeById(nodeIdToCntMap.get(startNodeId));
                    Node endNode = tx.getNodeById(nodeIdToCntMap.get(endNodeId));

                    cnt++;
                    Relationship relationship = startNode.createRelationshipTo(endNode, new DynamicRelationshipType(relationType));
                    relationship.setProperty("roadUid", roarUid);
                    relationship.setProperty("roadGrid", roadGrid);
                    relationship.setProperty("roadIndex", roadIndex);
                }
            }
            System.out.println("successfully imported edges");
            tx.commit();
        }
        // 导入100501.csv
        try ( Transaction tx = graphDb.database("neo4j").beginTx() ) {
            int cnt = 0;
            String line;
            try (BufferedReader br = new BufferedReader(new FileReader(TemporalFile))) {
                while ((line = br.readLine()) != null) {
                    cnt++;
                    String[] parts = line.split(" ");
                    // Split the second part by underscore to get two fields
                    String[] subParts = parts[1].split("_");
                    // Parse each part into an int and remove leading zeros
                    String timeStr = parts[0];
                    int gridId = Integer.parseInt(subParts[0]);
                    int chainId = Integer.parseInt(subParts[1]);
                    int congestionLevel = Integer.parseInt(parts[2]); // 拥堵程度
                    int numberOfVehicles = Integer.parseInt(parts[3]); // 链路车辆数
                    int travelTime = Integer.parseInt(parts[4]); // 旅行时间
                    TimePoint time = new TimePoint(Long.parseLong(timeStr));
                    Relationship relationship = tx.getRelationshipById(roadGridIndexToCntMap.get(new Pair<>(gridId, chainId)));
                    // 设置时态属性
                    relationship.setTemporalProperty("temp_congestionLevel", time, congestionLevel);
                    relationship.setTemporalProperty("temp_numberOfVehicles", time, numberOfVehicles);
                    relationship.setTemporalProperty("temp_travelTime", time, travelTime);
                    if(cnt % 100000 == 0) {
                        System.out.println("Imported " + cnt + " temporal properties");
                    }
                }
            }
            System.out.println("successfully imported temporal properties");
            tx.commit();
        }
        graphDb.shutdown();
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
