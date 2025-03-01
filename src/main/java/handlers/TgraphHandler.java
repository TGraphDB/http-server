package handlers;

import org.neo4j.graphdb.GraphDatabaseService;

public class TgraphHandler {
    private GraphDatabaseService graphDb;
    
    public TgraphHandler(GraphDatabaseService graphDb) {
        this.graphDb = graphDb;
    }

    // setGraphDb
    public void setGraphDb(GraphDatabaseService graphDb) {
        this.graphDb = graphDb;
    }
    
    
}
