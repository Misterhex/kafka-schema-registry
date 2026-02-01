package io.schemaregistry.mirror.storage.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonPropertyOrder(value = {"keytype", "subject", "magic"})
public class DeleteSubjectKey extends SchemaRegistryKey {

    private static final int MAGIC_BYTE = 0;
    private String subject;

    public DeleteSubjectKey(@JsonProperty("subject") String subject) {
        super(SchemaRegistryKeyType.DELETE_SUBJECT);
        this.magicByte = MAGIC_BYTE;
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
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        DeleteSubjectKey that = (DeleteSubjectKey) o;
        return subject != null ? subject.equals(that.subject) : that.subject == null;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (subject != null ? subject.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "{magic=" + magicByte + ",keytype=" + keyType.keyType + ",subject=" + subject + "}";
    }
}
