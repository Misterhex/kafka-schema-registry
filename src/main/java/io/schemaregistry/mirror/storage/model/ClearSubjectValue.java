package io.schemaregistry.mirror.storage.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ClearSubjectValue extends SchemaRegistryValue {

    private String subject;

    @JsonCreator
    public ClearSubjectValue(@JsonProperty("subject") String subject) {
        this.subject = subject;
    }

    @JsonProperty("subject")
    public String getSubject() {
        return subject;
    }

    @JsonProperty("subject")
    public void setSubject(String subject) {
        this.subject = subject;
    }

    @Override
    public ClearSubjectKey toKey() {
        return new ClearSubjectKey(subject);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ClearSubjectValue that = (ClearSubjectValue) o;
        return Objects.equals(subject, that.subject);
    }

    @Override
    public int hashCode() {
        return Objects.hash(subject);
    }

    @Override
    public String toString() {
        return "{subject=" + subject + "}";
    }
}
