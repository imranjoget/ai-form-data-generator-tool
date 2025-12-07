package org.joget.marketplace.datagenerator.model;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class Chat {
    
    private String model;
    private List<ChatGptMessage> messages;
    
    @SerializedName("max_tokens")
    private Integer maxTokens;
    
    private Double temperature;

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public List<ChatGptMessage> getMessages() {
        return messages;
    }

    public void setMessages(List<ChatGptMessage> messages) {
        this.messages = messages;
    }
    
    public Integer getMaxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(Integer maxTokens) {
        this.maxTokens = maxTokens;
    }

    public Double getTemperature() {
        return temperature;
    }

    public void setTemperature(Double temperature) {
        this.temperature = temperature;
    }
    
}
