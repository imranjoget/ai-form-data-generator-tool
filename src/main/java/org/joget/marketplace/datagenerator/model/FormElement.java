package org.joget.marketplace.datagenerator.model;

public class FormElement {

    private String id;
    private String label;
    private String value;
    private String type;
    private String fakerMapping;

    public FormElement(String id, String label, String value, String type) {
        this.id = id;
        this.label = label;
        this.value = value;
        this.type = type;
    }

    public String getId() {
        return id;
    }

    public String getLabel() {
        return label;
    }

    public void setId(String id) {
        this.id = id;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getFakerMapping() {
        return fakerMapping;
    }

    public void setFakerMapping(String fakerMapping) {
        this.fakerMapping = fakerMapping;
    }
    
}
