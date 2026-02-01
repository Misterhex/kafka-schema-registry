package io.schemaregistry.mirror.storage.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonPropertyOrder(value = {"keytype", "subject", "version", "magic"})
public class SchemaKey extends SchemaRegistryKey {

    private static final int MAGIC_BYTE = 1;
    private String subject;
    private int version;

    public SchemaKey(@JsonProperty("subject") String subject,
                     @JsonProperty("version") int version) {
        super(SchemaRegistryKeyType.SCHEMA);
        this.magicByte = MAGIC_BYTE;
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
    public int getVersion() {
        return version;
    }

    @JsonProperty("version")
    public void setVersion(int version) {
        this.version = version;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        SchemaKey that = (SchemaKey) o;
        return version == that.version && (subject != null ? subject.equals(that.subject) : that.subject == null);
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (subject != null ? subject.hashCode() : 0);
        result = 31 * result + version;
        return result;
    }

    @Override
    public int compareTo(SchemaRegistryKey o) {
        int compare = super.compareTo(o);
        if (compare == 0 && o instanceof SchemaKey other) {
            compare = this.subject.compareTo(other.subject);
            if (compare == 0) {
                compare = this.version - other.version;
            }
        }
        return compare;
    }

    @Override
    public String toString() {
        return "{magic=" + magicByte + ",keytype=" + keyType.keyType + ",subject=" + subject + ",version=" + version + "}";
    }
}
