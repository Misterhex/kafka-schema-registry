package io.schemaregistry.mirror.storage.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ModeValue extends SchemaRegistryValue {

    private String subject;
    private String mode;

    @JsonCreator
    public ModeValue(@JsonProperty("subject") String subject,
                     @JsonProperty("mode") String mode) {
        this.subject = subject;
        this.mode = mode;
    }

    public ModeValue() {
    }

    @JsonProperty("subject")
    public String getSubject() {
        return subject;
    }

    @JsonProperty("subject")
    public void setSubject(String subject) {
        this.subject = subject;
    }

    @JsonProperty("mode")
    public String getMode() {
        return mode;
    }

    @JsonProperty("mode")
    public void setMode(String mode) {
        this.mode = mode;
    }

    @Override
    public ModeKey toKey() {
        return new ModeKey(subject);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ModeValue that = (ModeValue) o;
        return Objects.equals(subject, that.subject) && Objects.equals(mode, that.mode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(subject, mode);
    }

    @Override
    public String toString() {
        return "{subject=" + subject + ",mode=" + mode + "}";
    }
}
