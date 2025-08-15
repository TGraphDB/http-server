package edu.buaa.common.transaction;

import java.io.PrintWriter;
import java.io.StringWriter;

public abstract class AbstractTransaction {
    public enum TxType{
        tx_index_tgraph_aggr_max(false),
        tx_index_tgraph_aggr_duration(false),
        tx_index_tgraph_temporal_condition(false),
        tx_import_static_data(false),
        tx_import_temporal_data(false),
        tx_update_temporal_data(false),
        tx_query_reachable_area(true),
        tx_query_node_neighbor_road(true),
        tx_query_road_earliest_arrive_time_aggr(true),
        tx_query_snapshot(true),
        tx_query_snapshot_aggr_max(true),
        tx_query_snapshot_aggr_duration(true),
        tx_query_road_by_temporal_condition(true),
        tx_query_entity_history(true);
        private boolean isReadTx;
        TxType(boolean isReadTx){
            this.isReadTx = isReadTx;
        }
        public boolean isReadTx() {
            return isReadTx;
        }
    }

    private static int idSeq = 0;
    public int id;
    private TxType txType;
    private Metrics metrics;
    private Result result;
    private int section = -1; // used when append tp. to ensure time asc for each entity.

    public AbstractTransaction(){
        this.id = idSeq++;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getSection() {
        return section;
    }

    public void setSection(int section) {
        this.section = section;
    }

    public TxType getTxType() {
        return txType;
    }

    public void setTxType(TxType txType) {
        this.txType = txType;
    }

    public Metrics getMetrics() {
        return metrics;
    }

    public void setMetrics(Metrics metrics) {
        this.metrics = metrics;
    }

    public Result getResult() {
        return result;
    }

    public void setResult(Result result) {
        this.result = result;
    }

    public boolean validateResult(Result result){return false;}

    public static class Metrics{
        private boolean txSuccess;
        private int waitTime; // duration, in milliseconds
        private long sendTime; // timestamp, in milliseconds
        private int exeTime; // duration, in milliseconds
        private int connId;
        private int reqSize; // user defined value, maybe bytes or rows
        private int returnSize; // user defined value, maybe bytes or rows
        private String errMsg;

        public String getErrMsg() {
            return errMsg;
        }

        public void setErrMsg(String errMsg){
            this.errMsg = errMsg;
        }

        public void setErrMsg(Throwable err) {
            final StringWriter sw = new StringWriter();
            final PrintWriter pw = new PrintWriter(sw, true);
            err.printStackTrace(pw);
            this.errMsg = sw.getBuffer().toString();
        }

        public boolean isTxSuccess() {
            return txSuccess;
        }

        public void setTxSuccess(boolean txSuccess) {
            this.txSuccess = txSuccess;
        }

        public int getExeTime() {
            return exeTime;
        }

        public void setExeTime(int exeTime) {
            this.exeTime = exeTime;
        }

        public int getWaitTime() {
            return waitTime;
        }

        public void setWaitTime(int waitTime) {
            this.waitTime = waitTime;
        }

        public long getSendTime() {
            return sendTime;
        }

        public void setSendTime(long sendTime) {
            this.sendTime = sendTime;
        }

        public int getConnId() {
            return connId;
        }

        public void setConnId(int connId) {
            this.connId = connId;
        }

        public int getReqSize() {
            return reqSize;
        }

        public void setReqSize(int reqSize) {
            this.reqSize = reqSize;
        }

        public int getReturnSize() {
            return returnSize;
        }

        public void setReturnSize(int returnSize) {
            this.returnSize = returnSize;
        }
    }

    public static class Result{

    }
}
