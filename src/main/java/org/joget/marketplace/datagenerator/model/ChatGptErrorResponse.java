
package org.joget.marketplace.datagenerator.model;

import java.util.LinkedHashMap;
import java.util.Map;
import javax.annotation.Generated;

@Generated("jsonschema2pojo")
public class ChatGptErrorResponse {

    private Error error;
    private Map<String, Object> additionalProperties = new LinkedHashMap<String, Object>();

    public Error getError() {
        return error;
    }

    public void setError(Error error) {
        this.error = error;
    }

    public Map<String, Object> getAdditionalProperties() {
        return this.additionalProperties;
    }

    public void setAdditionalProperty(String name, Object value) {
        this.additionalProperties.put(name, value);
    }

}
