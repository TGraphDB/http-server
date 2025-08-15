package edu.buaa.common.transaction;


import edu.buaa.common.utils.PFieldList;

public class UpdateTemporalDataTx extends AbstractTransaction {
    private PFieldList data = new PFieldList(); // start, end, eid, prop, value
    private boolean isNode;

    public UpdateTemporalDataTx() {
        this.setTxType(TxType.tx_update_temporal_data);
    }

    public UpdateTemporalDataTx(PFieldList data, boolean isNode) {
        this.isNode = isNode;
        this.setTxType(TxType.tx_update_temporal_data);
        this.data = data;
    }

    public PFieldList getData() {
        return data;
    }

    public void setData(PFieldList data) {
        this.data = data;
    }

    public boolean isNode() {
        return isNode;
    }

    public void setNode(boolean node) {
        isNode = node;
    }
}