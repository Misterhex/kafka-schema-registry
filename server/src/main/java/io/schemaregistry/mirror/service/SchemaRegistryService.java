package io.schemaregistry.mirror.service;

import io.confluent.kafka.schemaregistry.client.rest.entities.Schema;
import io.confluent.kafka.schemaregistry.client.rest.entities.SchemaString;
import io.confluent.kafka.schemaregistry.client.rest.entities.SubjectVersion;
import io.confluent.kafka.schemaregistry.client.rest.entities.requests.RegisterSchemaRequest;
import io.schemaregistry.mirror.schema.CompatibilityLevel;

import java.util.List;
import java.util.Map;

public interface SchemaRegistryService {

    // Schema read operations
    Schema getSchemaById(int id, String subject, boolean fetchMaxId);

    SchemaString getSchemaStringById(int id, String subject, boolean fetchMaxId);

    String getRawSchemaById(int id, String subject);

    List<String> getSubjectsForSchemaId(int id, boolean lookupDeletedSubjects);

    List<SubjectVersion> getVersionsForSchemaId(int id, boolean lookupDeletedSubjects);

    List<String> getSchemaTypes();

    // Subject operations
    List<String> listSubjects(String subjectPrefix, boolean lookupDeletedSubjects);

    // Version operations
    List<Integer> listVersions(String subject, boolean lookupDeletedVersions);

    Schema getSchemaByVersion(String subject, String version, boolean lookupDeletedSchema);

    String getRawSchemaByVersion(String subject, String version);

    List<Integer> getReferencedBy(String subject, String version);

    // Register
    int registerSchema(String subject, RegisterSchemaRequest request, boolean normalize);

    // Lookup
    Schema lookupSchema(String subject, RegisterSchemaRequest request, boolean normalize, boolean lookupDeletedSchema);

    // Delete
    List<Integer> deleteSubject(String subject, boolean permanent);

    int deleteSchemaVersion(String subject, String version, boolean permanent);

    // Compatibility
    List<String> testCompatibility(String subject, String version, RegisterSchemaRequest request, boolean verbose);

    boolean isCompatible(String subject, String version, RegisterSchemaRequest request);

    // Config
    Map<String, String> getGlobalConfig();

    Map<String, String> setGlobalConfig(CompatibilityLevel level);

    Map<String, String> getSubjectConfig(String subject, boolean defaultToGlobal);

    Map<String, String> setSubjectConfig(String subject, CompatibilityLevel level);

    Map<String, String> deleteSubjectConfig(String subject);

    // Mode
    Map<String, String> getGlobalMode();

    Map<String, String> setGlobalMode(String mode, boolean force);

    Map<String, String> getSubjectMode(String subject, boolean defaultToGlobal);

    Map<String, String> setSubjectMode(String subject, String mode, boolean force);

    Map<String, String> deleteSubjectMode(String subject);
}
