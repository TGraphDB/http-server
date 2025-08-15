package edu.buaa.common.transaction;


import edu.buaa.common.utils.PFieldList;

public class ImportStaticDataTx extends AbstractTransaction {
    private PFieldList nodes;
    private PFieldList rels;

    public ImportStaticDataTx() {
        this.setTxType(TxType.tx_import_static_data);
    }

    public PFieldList getNodes() {
        return nodes;
    }

    public void setNodes(PFieldList nodes) {
        this.nodes = nodes;
    }

    public PFieldList getRels() {
        return rels;
    }

    public void setRels(PFieldList rels) {
        this.rels = rels;
    }


}
