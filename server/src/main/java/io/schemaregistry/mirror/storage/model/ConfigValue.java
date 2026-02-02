package io.schemaregistry.mirror.storage.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.schemaregistry.mirror.schema.CompatibilityLevel;

import java.util.Objects;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ConfigValue extends SchemaRegistryValue {

    private String subject;
    private CompatibilityLevel compatibilityLevel;

    @JsonCreator
    public ConfigValue(@JsonProperty("subject") String subject,
                       @JsonProperty("compatibilityLevel") CompatibilityLevel compatibilityLevel) {
        this.subject = subject;
        this.compatibilityLevel = compatibilityLevel;
    }

    public ConfigValue() {
    }

    @JsonProperty("subject")
    public String getSubject() {
        return subject;
    }

    @JsonProperty("subject")
    public void setSubject(String subject) {
        this.subject = subject;
    }

    @JsonProperty("compatibilityLevel")
    public CompatibilityLevel getCompatibilityLevel() {
        return compatibilityLevel;
    }

    @JsonProperty("compatibilityLevel")
    public void setCompatibilityLevel(CompatibilityLevel compatibilityLevel) {
        this.compatibilityLevel = compatibilityLevel;
    }

    @Override
    public ConfigKey toKey() {
        return new ConfigKey(subject);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ConfigValue that = (ConfigValue) o;
        return Objects.equals(subject, that.subject)
            && compatibilityLevel == that.compatibilityLevel;
    }

    @Override
    public int hashCode() {
        return Objects.hash(subject, compatibilityLevel);
    }

    @Override
    public String toString() {
        return "{subject=" + subject + ",compatibilityLevel=" + compatibilityLevel + "}";
    }
}
