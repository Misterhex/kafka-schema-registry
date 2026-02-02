package io.schemaregistry.mirror.storage.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.confluent.kafka.schemaregistry.client.rest.entities.Metadata;
import io.confluent.kafka.schemaregistry.client.rest.entities.RuleSet;
import io.confluent.kafka.schemaregistry.client.rest.entities.SchemaReference;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonIgnoreProperties(ignoreUnknown = true)
public class SchemaValue extends SchemaRegistryValue implements Comparable<SchemaValue> {

    private String subject;
    private Integer version;
    private Integer id;
    private String md5;
    private String schemaType = "AVRO";
    private List<SchemaReference> references = Collections.emptyList();
    private Metadata metadata;
    private RuleSet ruleSet;
    private String schema;
    private boolean deleted;

    public SchemaValue() {
        // default constructor
    }

    @JsonCreator
    public SchemaValue(@JsonProperty("subject") String subject,
                       @JsonProperty("version") Integer version,
                       @JsonProperty("id") Integer id,
                       @JsonProperty("md5") String md5,
                       @JsonProperty("schemaType") String schemaType,
                       @JsonProperty("references") List<SchemaReference> references,
                       @JsonProperty("metadata") Metadata metadata,
                       @JsonProperty("ruleSet") RuleSet ruleSet,
                       @JsonProperty("schema") String schema,
                       @JsonProperty("deleted") boolean deleted) {
        this.subject = subject;
        this.version = version;
        this.id = id;
        this.md5 = md5;
        this.schemaType = schemaType != null ? schemaType : "AVRO";
        this.references = references != null ? references : Collections.emptyList();
        this.metadata = metadata;
        this.ruleSet = ruleSet;
        this.schema = schema;
        this.deleted = deleted;
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

    @JsonProperty("id")
    public Integer getId() {
        return id;
    }

    @JsonProperty("id")
    public void setId(Integer id) {
        this.id = id;
    }

    @JsonProperty("md5")
    public String getMd5() {
        return md5;
    }

    @JsonProperty("md5")
    public void setMd5(String md5) {
        this.md5 = md5;
    }

    @JsonProperty("schemaType")
    public String getSchemaType() {
        return schemaType;
    }

    @JsonProperty("schemaType")
    public void setSchemaType(String schemaType) {
        this.schemaType = schemaType != null ? schemaType : "AVRO";
    }

    @JsonProperty("references")
    public List<SchemaReference> getReferences() {
        return references;
    }

    @JsonProperty("references")
    public void setReferences(List<SchemaReference> references) {
        this.references = references;
    }

    @JsonProperty("metadata")
    public Metadata getMetadata() {
        return metadata;
    }

    @JsonProperty("metadata")
    public void setMetadata(Metadata metadata) {
        this.metadata = metadata;
    }

    @JsonProperty("ruleSet")
    public RuleSet getRuleSet() {
        return ruleSet;
    }

    @JsonProperty("ruleSet")
    public void setRuleSet(RuleSet ruleSet) {
        this.ruleSet = ruleSet;
    }

    @JsonProperty("schema")
    public String getSchema() {
        return schema;
    }

    @JsonProperty("schema")
    public void setSchema(String schema) {
        this.schema = schema;
    }

    @JsonProperty("deleted")
    public boolean isDeleted() {
        return deleted;
    }

    @JsonProperty("deleted")
    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }

    @Override
    public SchemaKey toKey() {
        return new SchemaKey(subject, version);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SchemaValue that = (SchemaValue) o;
        return deleted == that.deleted
            && Objects.equals(subject, that.subject)
            && Objects.equals(version, that.version)
            && Objects.equals(id, that.id)
            && Objects.equals(md5, that.md5)
            && Objects.equals(schema, that.schema)
            && Objects.equals(schemaType, that.schemaType)
            && Objects.equals(references, that.references)
            && Objects.equals(metadata, that.metadata)
            && Objects.equals(ruleSet, that.ruleSet);
    }

    @Override
    public int hashCode() {
        return Objects.hash(subject, version, id, md5, schema, schemaType, references, metadata, ruleSet, deleted);
    }

    @Override
    public int compareTo(SchemaValue that) {
        int result = this.subject.compareTo(that.subject);
        if (result != 0) return result;
        return this.version - that.version;
    }

    @Override
    public String toString() {
        return "{subject=" + subject + ",version=" + version + ",id=" + id + ",schemaType=" + schemaType
            + ",deleted=" + deleted + "}";
    }
}
