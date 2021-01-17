package com.roastgg.options.beans;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

public class StockData {

    @JsonProperty("Meta Data")
    Map<String, String> metadata;

    @JsonProperty("Time Series (Daily)")
    Map<String, Map<String,String>> daily;

    public Map<String, String> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, String> metadata) {
        this.metadata = metadata;
    }

    public Map<String, Map<String, String>> getDaily() {
        return daily;
    }

    public void setDaily(Map<String, Map<String, String>> daily) {
        this.daily = daily;
    }
}
