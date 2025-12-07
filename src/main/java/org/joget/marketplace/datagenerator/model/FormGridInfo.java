package org.joget.marketplace.datagenerator.model;

public class FormGridInfo {

    public String id;
    public String formDefId;
    public String loadBinderFormDefId;
    public String loadBinderForeignKey;
    public String storeBinderFormDefId;
    public String storeBinderForeignKey;

    public FormGridInfo(String id, String formDefId,
            String loadBinderFormDefId, String loadBinderForeignKey,
            String storeBinderFormDefId, String storeBinderForeignKey) {
        this.id = id;
        this.formDefId = formDefId;
        this.loadBinderFormDefId = loadBinderFormDefId;
        this.loadBinderForeignKey = loadBinderForeignKey;
        this.storeBinderFormDefId = storeBinderFormDefId;
        this.storeBinderForeignKey = storeBinderForeignKey;
    }
}
