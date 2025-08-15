package edu.buaa.common.transaction;


import edu.buaa.common.utils.PFieldList;

public class ImportTemporalDataTx extends AbstractTransaction {
    public PFieldList data = new PFieldList();
    private boolean isNode;

    public ImportTemporalDataTx() {
        this.setTxType(TxType.tx_import_temporal_data);
    }
    // default constructor and getter setter are needed by json encode/decode.

    public ImportTemporalDataTx(PFieldList lines, boolean isNode) {
        this.setTxType(TxType.tx_import_temporal_data);
        this.isNode = isNode;
        this.data = lines;
        Metrics m = new Metrics();
        m.setReqSize(lines.size());
        this.setMetrics(m);
    }

    public boolean isNode() {
        return isNode;
    }

    public void setNode(boolean node) {
        isNode = node;
    }

    public PFieldList getData() {
        return data;
    }

    public void setData(PFieldList data) {
        this.data = data;
    }

}
