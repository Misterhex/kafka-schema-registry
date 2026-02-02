package io.schemaregistry.mirror.storage.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonIgnoreProperties(ignoreUnknown = true)
public class DeleteSubjectValue extends SchemaRegistryValue {

    private String subject;
    private Integer version;

    @JsonCreator
    public DeleteSubjectValue(@JsonProperty("subject") String subject,
                              @JsonProperty("version") Integer version) {
        this.subject = subject;
        this.version = version;
    }

    @JsonProperty("subject")
    public String getSubject() {
        return subject;
    }

    @JsonProperty("subject")
    public void setSubject(String subject) {
        this.subject = subject;
    }

    @JsonProperty("version")
    public Integer getVersion() {
        return version;
    }

    @JsonProperty("version")
    public void setVersion(Integer version) {
        this.version = version;
    }

    @Override
    public DeleteSubjectKey toKey() {
        return new DeleteSubjectKey(subject);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DeleteSubjectValue that = (DeleteSubjectValue) o;
        return Objects.equals(subject, that.subject) && Objects.equals(version, that.version);
    }

    @Override
    public int hashCode() {
        return Objects.hash(subject, version);
    }

    @Override
    public String toString() {
        return "{subject=" + subject + ",version=" + version + "}";
    }
}
