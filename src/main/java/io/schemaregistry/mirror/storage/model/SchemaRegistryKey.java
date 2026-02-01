package io.schemaregistry.mirror.storage.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "keytype", visible = true)
@JsonSubTypes({
    @JsonSubTypes.Type(value = SchemaKey.class, name = "SCHEMA"),
    @JsonSubTypes.Type(value = ConfigKey.class, name = "CONFIG"),
    @JsonSubTypes.Type(value = ModeKey.class, name = "MODE"),
    @JsonSubTypes.Type(value = DeleteSubjectKey.class, name = "DELETE_SUBJECT"),
    @JsonSubTypes.Type(value = ClearSubjectKey.class, name = "CLEAR_SUBJECT"),
    @JsonSubTypes.Type(value = NoopKey.class, name = "NOOP")
})
public abstract class SchemaRegistryKey implements Comparable<SchemaRegistryKey> {

    protected int magicByte;
    protected SchemaRegistryKeyType keyType;

    public SchemaRegistryKey(SchemaRegistryKeyType keyType) {
        this.keyType = keyType;
    }

    @JsonProperty("magic")
    public int getMagicByte() {
        return magicByte;
    }

    @JsonProperty("magic")
    public void setMagicByte(int magicByte) {
        this.magicByte = magicByte;
    }

    @JsonProperty("keytype")
    public SchemaRegistryKeyType getKeyType() {
        return keyType;
    }

    @JsonProperty("keytype")
    public void setKeyType(SchemaRegistryKeyType keyType) {
        this.keyType = keyType;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SchemaRegistryKey that = (SchemaRegistryKey) o;
        return magicByte == that.magicByte && keyType == that.keyType;
    }

    @Override
    public int hashCode() {
        int result = magicByte;
        result = 31 * result + (keyType != null ? keyType.hashCode() : 0);
        return result;
    }

    @Override
    public int compareTo(SchemaRegistryKey other) {
        return this.keyType.compareTo(other.keyType);
    }
}
