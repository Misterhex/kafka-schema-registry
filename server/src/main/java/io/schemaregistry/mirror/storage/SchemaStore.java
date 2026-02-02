package io.schemaregistry.mirror.storage;

import io.schemaregistry.mirror.schema.CompatibilityLevel;
import io.schemaregistry.mirror.storage.model.SchemaValue;

import java.util.List;
import java.util.Map;

public interface SchemaStore {

    void start() throws Exception;

    void stop();

    boolean initialized();

    void waitForInit() throws InterruptedException;

    // Schema read operations
    SchemaValue getSchemaById(int id);

    SchemaValue getSchema(String subject, int version, boolean lookupDeletedSchema);

    List<Integer> getVersions(String subject, boolean lookupDeletedSchema);

    List<String> getSubjects(boolean lookupDeletedSubjects);

    int getLatestVersion(String subject, boolean lookupDeletedSchema);

    boolean hasSubject(String subject, boolean lookupDeletedSubjects);

    List<SchemaValue> getSchemasBySubject(String subject, boolean lookupDeletedSchema);

    SchemaValue lookupSchemaByContent(String subject, String schema, String schemaType,
                                      List<io.confluent.kafka.schemaregistry.client.rest.entities.SchemaReference> references,
                                      boolean lookupDeletedSchema);

    List<String> getSubjectsForSchemaId(int id, boolean lookupDeletedSubjects);

    List<Map<String, Object>> getVersionsForSchemaId(int id, boolean lookupDeletedSubjects);

    List<Integer> getReferencedBy(String subject, int version);

    int getMaxSchemaId();

    // Schema write operations
    void registerSchema(SchemaValue schemaValue);

    void softDeleteSchema(String subject, int version);

    void hardDeleteSchema(String subject, int version);

    void softDeleteSubject(String subject);

    void hardDeleteSubject(String subject);

    // Config operations
    CompatibilityLevel getGlobalCompatibilityLevel();

    void setGlobalCompatibilityLevel(CompatibilityLevel level);

    CompatibilityLevel getSubjectCompatibilityLevel(String subject);

    void setSubjectCompatibilityLevel(String subject, CompatibilityLevel level);

    void deleteSubjectCompatibilityLevel(String subject);

    boolean hasSubjectCompatibilityLevel(String subject);

    // Mode operations
    String getGlobalMode();

    void setGlobalMode(String mode);

    String getSubjectMode(String subject);

    void setSubjectMode(String subject, String mode);

    void deleteSubjectMode(String subject);

    boolean hasSubjectMode(String subject);
}
