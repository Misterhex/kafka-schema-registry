package io.schemaregistry.mirror.storage.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonPropertyOrder(value = {"keytype", "magic"})
public class NoopKey extends SchemaRegistryKey {

    private static final int MAGIC_BYTE = 0;

    public NoopKey() {
        super(SchemaRegistryKeyType.NOOP);
        this.magicByte = MAGIC_BYTE;
    }

    @Override
    public String toString() {
        return "{magic=" + magicByte + ",keytype=" + keyType.keyType + "}";
    }
}
