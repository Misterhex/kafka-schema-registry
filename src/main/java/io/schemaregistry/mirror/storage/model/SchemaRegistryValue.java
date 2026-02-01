package io.schemaregistry.mirror.storage.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonIgnoreProperties(ignoreUnknown = true)
public abstract class SchemaRegistryValue {

    protected Long offset;
    protected Long timestamp;

    @JsonProperty("offset")
    public Long getOffset() {
        return offset;
    }

    @JsonProperty("offset")
    public void setOffset(Long offset) {
        this.offset = offset;
    }

    @JsonProperty("ts")
    public Long getTimestamp() {
        return timestamp;
    }

    @JsonProperty("ts")
    public void setTimestamp(Long timestamp) {
        this.timestamp = timestamp;
    }

    public abstract SchemaRegistryKey toKey();
}
