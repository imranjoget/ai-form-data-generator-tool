package org.joget.marketplace.datagenerator.model;

public class SpreadSheetGridInfo {

    private final String id;
    private final String loadBinderFormDefId;
    private final String loadBinderForeignKey;
    private final String storeBinderFormDefId;
    private final String storeBinderForeignKey;

    public SpreadSheetGridInfo(String id,
            String loadBinderFormDefId, String loadBinderForeignKey,
            String storeBinderFormDefId, String storeBinderForeignKey) {

        this.id = id;
        this.loadBinderFormDefId = loadBinderFormDefId;
        this.loadBinderForeignKey = loadBinderForeignKey;
        this.storeBinderFormDefId = storeBinderFormDefId;
        this.storeBinderForeignKey = storeBinderForeignKey;
    }

    public String getId() {
        return id;
    }

    public String getLoadBinderFormDefId() {
        return loadBinderFormDefId;
    }

    public String getLoadBinderForeignKey() {
        return loadBinderForeignKey;
    }

    public String getStoreBinderFormDefId() {
        return storeBinderFormDefId;
    }

    public String getStoreBinderForeignKey() {
        return storeBinderForeignKey;
    }
}
