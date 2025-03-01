package handlers;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.neo4j.graphdb.GraphDatabaseService;

import io.javalin.http.Context;

import tgraph.Tgraph;
import app.Application;

public class TgraphHandler {
    private GraphDatabaseService graphDb;
    private final Tgraph tgraph; // 可以是final的
    
    public TgraphHandler(GraphDatabaseService graphDb, Tgraph tgraph) {
        this.graphDb = graphDb;
        this.tgraph = tgraph;
    }

    // setGraphDb
    public void setGraphDb(GraphDatabaseService graphDb) {
        this.graphDb = graphDb;
    }
    
    // 创建数据库
    public void createDatabase(Context ctx) {
        String databaseName = ctx.pathParam("databaseName");
        if (graphDb != null) {
            Map<String, Object> errorResponse = new HashMap<>();
            List<Map<String, String>> errors = new ArrayList<>();
            Map<String, String> error = new HashMap<>();
            error.put("message", "已有数据库在运行，请先关闭当前数据库");
            error.put("code", "Neo.ClientError.General.DatabaseError");
            errors.add(error);
            errorResponse.put("errors", errors);
            ctx.status(409).json(errorResponse); // 409 Conflict
            return;
        }
        try {
            graphDb = tgraph.createDb(databaseName);
        } catch (Exception e) {
            // TODO: handle exception
            e.printStackTrace();
        }
        Application.updateHandlers(graphDb); // 更新handlers
        ctx.status(201);
    }

    // 启动数据库
    public void startDatabase(Context ctx) {
        String databaseName = ctx.pathParam("databaseName");
        if (graphDb != null) {
            Map<String, Object> errorResponse = new HashMap<>();
            List<Map<String, String>> errors = new ArrayList<>();
            Map<String, String> error = new HashMap<>();
            error.put("message", "已有数据库在运行，请先关闭当前数据库");
            error.put("code", "Neo.ClientError.General.DatabaseError");
            errors.add(error);
            errorResponse.put("errors", errors);
            ctx.status(409).json(errorResponse); // 409 Conflict
            return;
        }
        graphDb = tgraph.startDb(databaseName);
        Application.updateHandlers(graphDb); // 更新handlers
        ctx.status(201);
    }

    // 删除数据库
    public void deleteDatabase(Context ctx) {
        String databaseName = ctx.pathParam("databaseName");
        boolean isDelete = tgraph.deleteDb(databaseName);
        if (isDelete) {
            ctx.status(204);
        } else {
            Map<String, Object> errorResponse = new HashMap<>();
            List<Map<String, String>> errors = new ArrayList<>();
            Map<String, String> error = new HashMap<>();
            error.put("message", "删除数据库 '" + databaseName + "' 失败");
            error.put("code", "Neo.ClientError.General.DatabaseError");
            errors.add(error);
            errorResponse.put("errors", errors);
            ctx.status(500).json(errorResponse);
        }
    }

    // 关闭数据库 由于一个时间只能有一个数据库被打开 所以不用传入{databaseName}
    public void shutdownDatabase(Context ctx) {
        tgraph.shutDown(graphDb);
        graphDb = null;
        Application.updateHandlers(null); // 清空handlers中的graphDb
        ctx.status(204);
    }

    public void backupDatabase(Context ctx) {
        String databaseName = ctx.pathParam("databaseName");
        try {
            tgraph.backupDatabase(databaseName);
            ctx.status(201);
        } catch (IllegalStateException e) {
            // 数据库正在运行的错误处理
            Map<String, Object> errorResponse = new HashMap<>();
            List<Map<String, String>> errors = new ArrayList<>();
            Map<String, String> error = new HashMap<>();
            error.put("message", e.getMessage());
            error.put("code", "Neo.ClientError.General.DatabaseError");
            errors.add(error);
            errorResponse.put("errors", errors);
            ctx.status(409).json(errorResponse);
        } catch (IllegalArgumentException e) {
            // 数据库不存在的错误处理
            Map<String, Object> errorResponse = new HashMap<>();
            List<Map<String, String>> errors = new ArrayList<>();
            Map<String, String> error = new HashMap<>();
            error.put("message", e.getMessage());
            error.put("code", "Neo.ClientError.General.DatabaseNotFound");
            errors.add(error);
            errorResponse.put("errors", errors);
            ctx.status(404).json(errorResponse);
        } catch (IOException e) {
            // IO错误处理
            Map<String, Object> errorResponse = new HashMap<>();
            List<Map<String, String>> errors = new ArrayList<>();
            Map<String, String> error = new HashMap<>();
            error.put("message", "备份数据库时发生错误: " + e.getMessage());
            error.put("code", "Neo.ClientError.General.IOError");
            errors.add(error);
            errorResponse.put("errors", errors);
            ctx.status(500).json(errorResponse);
        }
    }

    public void restoreDatabase(Context ctx) {
        String databaseName = ctx.pathParam("databaseName");
        try {
            tgraph.restoreDatabase(databaseName);
            ctx.status(201);
        } catch (IllegalStateException e) {
            // 数据库正在运行或目标数据库已存在的错误处理
            Map<String, Object> errorResponse = new HashMap<>();
            List<Map<String, String>> errors = new ArrayList<>();
            Map<String, String> error = new HashMap<>();
            error.put("message", e.getMessage());
            error.put("code", "Neo.ClientError.General.DatabaseError");
            errors.add(error);
            errorResponse.put("errors", errors);
            ctx.status(409).json(errorResponse);
        } catch (IllegalArgumentException e) {
            // 备份文件不存在的错误处理
            Map<String, Object> errorResponse = new HashMap<>();
            List<Map<String, String>> errors = new ArrayList<>();
            Map<String, String> error = new HashMap<>();
            error.put("message", e.getMessage());
            error.put("code", "Neo.ClientError.General.BackupNotFound");
            errors.add(error);
            errorResponse.put("errors", errors);
            ctx.status(404).json(errorResponse);
        } catch (IOException e) {
            // IO错误处理
            Map<String, Object> errorResponse = new HashMap<>();
            List<Map<String, String>> errors = new ArrayList<>();
            Map<String, String> error = new HashMap<>();
            error.put("message", "恢复数据库时发生错误: " + e.getMessage());
            error.put("code", "Neo.ClientError.General.IOError");
            errors.add(error);
            errorResponse.put("errors", errors);
            ctx.status(500).json(errorResponse);
        }
    }

    /*
        // 创建数据库
        app.post("/db/data/database/{databaseName}/create", ctx -> {
            String databaseName = ctx.pathParam("databaseName");
            if (graphDb != null) {
                Map<String, Object> errorResponse = new HashMap<>();
                List<Map<String, String>> errors = new ArrayList<>();
                Map<String, String> error = new HashMap<>();
                error.put("message", "已有数据库在运行，请先关闭当前数据库");
                error.put("code", "Neo.ClientError.General.DatabaseError");
                errors.add(error);
                errorResponse.put("errors", errors);
                ctx.status(409).json(errorResponse); // 409 Conflict
                return;
            }
            graphDb = tgraph.createDb(databaseName);
            updateHandlers(graphDb); // 更新handlers
            ctx.status(201);
        });

        // 启动数据库
        app.post("/db/data/database/{databaseName}/start", ctx -> {
            String databaseName = ctx.pathParam("databaseName");
            if (graphDb != null) {
                Map<String, Object> errorResponse = new HashMap<>();
                List<Map<String, String>> errors = new ArrayList<>();
                Map<String, String> error = new HashMap<>();
                error.put("message", "已有数据库在运行，请先关闭当前数据库");
                error.put("code", "Neo.ClientError.General.DatabaseError");
                errors.add(error);
                errorResponse.put("errors", errors);
                ctx.status(409).json(errorResponse); // 409 Conflict
                return;
            }
            graphDb = tgraph.startDb(databaseName);
            updateHandlers(graphDb); // 更新handlers
            ctx.status(201);
        });

        // 删除数据库
        app.delete("/db/data/database/{databaseName}", ctx -> {
            String databaseName = ctx.pathParam("databaseName");
            boolean isDelete = tgraph.deleteDb(databaseName);
            if (isDelete) {
                ctx.status(204);
            } else {
                Map<String, Object> errorResponse = new HashMap<>();
                List<Map<String, String>> errors = new ArrayList<>();
                Map<String, String> error = new HashMap<>();
                error.put("message", "删除数据库 '" + databaseName + "' 失败");
                error.put("code", "Neo.ClientError.General.DatabaseError");
                errors.add(error);
                errorResponse.put("errors", errors);
                ctx.status(500).json(errorResponse);
            }
        });
        
        // 关闭数据库 由于一个时间只能有一个数据库被打开 所以不用传入{databaseName}
        app.post("/db/data/database", ctx -> {
            tgraph.shutDown(graphDb);
            graphDb = null;
            updateHandlers(null); // 清空handlers中的graphDb
            ctx.status(204);
        });

        // 备份数据库
        app.post("/db/data/database/{databaseName}/backup", ctx -> {
            String databaseName = ctx.pathParam("databaseName");
            try {
                tgraph.backupDatabase(databaseName);
                ctx.status(201);
            } catch (IllegalStateException e) {
                // 数据库正在运行的错误处理
                Map<String, Object> errorResponse = new HashMap<>();
                List<Map<String, String>> errors = new ArrayList<>();
                Map<String, String> error = new HashMap<>();
                error.put("message", e.getMessage());
                error.put("code", "Neo.ClientError.General.DatabaseError");
                errors.add(error);
                errorResponse.put("errors", errors);
                ctx.status(409).json(errorResponse);
            } catch (IllegalArgumentException e) {
                // 数据库不存在的错误处理
                Map<String, Object> errorResponse = new HashMap<>();
                List<Map<String, String>> errors = new ArrayList<>();
                Map<String, String> error = new HashMap<>();
                error.put("message", e.getMessage());
                error.put("code", "Neo.ClientError.General.DatabaseNotFound");
                errors.add(error);
                errorResponse.put("errors", errors);
                ctx.status(404).json(errorResponse);
            } catch (IOException e) {
                // IO错误处理
                Map<String, Object> errorResponse = new HashMap<>();
                List<Map<String, String>> errors = new ArrayList<>();
                Map<String, String> error = new HashMap<>();
                error.put("message", "备份数据库时发生错误: " + e.getMessage());
                error.put("code", "Neo.ClientError.General.IOError");
                errors.add(error);
                errorResponse.put("errors", errors);
                ctx.status(500).json(errorResponse);
            }
        });

        // 恢复数据库
        app.post("/db/data/database/{databaseName}/restore", ctx -> {
            String databaseName = ctx.pathParam("databaseName");
            try {
                tgraph.restoreDatabase(databaseName);
                ctx.status(201);
            } catch (IllegalStateException e) {
                // 数据库正在运行或目标数据库已存在的错误处理
                Map<String, Object> errorResponse = new HashMap<>();
                List<Map<String, String>> errors = new ArrayList<>();
                Map<String, String> error = new HashMap<>();
                error.put("message", e.getMessage());
                error.put("code", "Neo.ClientError.General.DatabaseError");
                errors.add(error);
                errorResponse.put("errors", errors);
                ctx.status(409).json(errorResponse);
            } catch (IllegalArgumentException e) {
                // 备份文件不存在的错误处理
                Map<String, Object> errorResponse = new HashMap<>();
                List<Map<String, String>> errors = new ArrayList<>();
                Map<String, String> error = new HashMap<>();
                error.put("message", e.getMessage());
                error.put("code", "Neo.ClientError.General.BackupNotFound");
                errors.add(error);
                errorResponse.put("errors", errors);
                ctx.status(404).json(errorResponse);
            } catch (IOException e) {
                // IO错误处理
                Map<String, Object> errorResponse = new HashMap<>();
                List<Map<String, String>> errors = new ArrayList<>();
                Map<String, String> error = new HashMap<>();
                error.put("message", "恢复数据库时发生错误: " + e.getMessage());
                error.put("code", "Neo.ClientError.General.IOError");
                errors.add(error);
                errorResponse.put("errors", errors);
                ctx.status(500).json(errorResponse);
            }
        });
    */



}
