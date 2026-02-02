package io.schemaregistry.mirror.service;

import io.confluent.kafka.schemaregistry.ParsedSchema;
import io.confluent.kafka.schemaregistry.client.rest.entities.Schema;
import io.confluent.kafka.schemaregistry.client.rest.entities.SchemaReference;
import io.confluent.kafka.schemaregistry.client.rest.entities.SchemaString;
import io.confluent.kafka.schemaregistry.client.rest.entities.SubjectVersion;
import io.confluent.kafka.schemaregistry.client.rest.entities.requests.RegisterSchemaRequest;
import io.schemaregistry.mirror.exception.SchemaRegistryException;
import io.schemaregistry.mirror.schema.CompatibilityLevel;
import io.schemaregistry.mirror.storage.KafkaSchemaStore;
import io.schemaregistry.mirror.storage.model.SchemaValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class SchemaRegistryServiceImpl implements SchemaRegistryService {

    private static final Logger log = LoggerFactory.getLogger(SchemaRegistryServiceImpl.class);

    private final KafkaSchemaStore store;
    private final CompatibilityService compatibilityService;

    public SchemaRegistryServiceImpl(KafkaSchemaStore store, CompatibilityService compatibilityService) {
        this.store = store;
        this.compatibilityService = compatibilityService;
    }

    // --- Schema read operations ---

    @Override
    public Schema getSchemaById(int id, String subject, boolean fetchMaxId) {
        SchemaValue sv = store.getSchemaById(id);
        if (sv == null) {
            throw SchemaRegistryException.schemaNotFoundException(id);
        }
        Schema schema = toSchemaEntity(sv);
        return schema;
    }

    @Override
    public SchemaString getSchemaStringById(int id, String subject, boolean fetchMaxId) {
        SchemaValue sv = store.getSchemaById(id);
        if (sv == null) {
            throw SchemaRegistryException.schemaNotFoundException(id);
        }
        SchemaString schemaString = new SchemaString(sv.getSchema());
        schemaString.setSchemaType(sv.getSchemaType());
        if (sv.getReferences() != null && !sv.getReferences().isEmpty()) {
            schemaString.setReferences(sv.getReferences());
        }
        if (fetchMaxId) {
            schemaString.setMaxId(store.getMaxSchemaId());
        }
        return schemaString;
    }

    @Override
    public String getRawSchemaById(int id, String subject) {
        SchemaValue sv = store.getSchemaById(id);
        if (sv == null) {
            throw SchemaRegistryException.schemaNotFoundException(id);
        }
        return sv.getSchema();
    }

    @Override
    public List<String> getSubjectsForSchemaId(int id, boolean lookupDeletedSubjects) {
        SchemaValue sv = store.getSchemaById(id);
        if (sv == null) {
            throw SchemaRegistryException.schemaNotFoundException(id);
        }
        return store.getSubjectsForSchemaId(id, lookupDeletedSubjects);
    }

    @Override
    public List<SubjectVersion> getVersionsForSchemaId(int id, boolean lookupDeletedSubjects) {
        SchemaValue sv = store.getSchemaById(id);
        if (sv == null) {
            throw SchemaRegistryException.schemaNotFoundException(id);
        }
        List<Map<String, Object>> versions = store.getVersionsForSchemaId(id, lookupDeletedSubjects);
        return versions.stream()
            .map(m -> new SubjectVersion((String) m.get("subject"), (Integer) m.get("version")))
            .collect(Collectors.toList());
    }

    @Override
    public List<String> getSchemaTypes() {
        return compatibilityService.getSupportedTypes();
    }

    // --- Subject operations ---

    @Override
    public List<String> listSubjects(String subjectPrefix, boolean lookupDeletedSubjects) {
        List<String> subjects = store.getSubjects(lookupDeletedSubjects);
        if (subjectPrefix != null && !subjectPrefix.isEmpty()) {
            return subjects.stream()
                .filter(s -> s.startsWith(subjectPrefix))
                .collect(Collectors.toList());
        }
        return subjects;
    }

    // --- Version operations ---

    @Override
    public List<Integer> listVersions(String subject, boolean lookupDeletedVersions) {
        if (!store.hasSubject(subject, true)) {
            throw SchemaRegistryException.subjectNotFoundException(subject);
        }
        if (!lookupDeletedVersions && !store.hasSubject(subject, false)) {
            throw SchemaRegistryException.subjectNotFoundException(subject);
        }
        return store.getVersions(subject, lookupDeletedVersions);
    }

    @Override
    public Schema getSchemaByVersion(String subject, String version, boolean lookupDeletedSchema) {
        int versionInt = resolveVersion(subject, version, lookupDeletedSchema);

        if (!store.hasSubject(subject, true)) {
            throw SchemaRegistryException.subjectNotFoundException(subject);
        }

        SchemaValue sv = store.getSchema(subject, versionInt, lookupDeletedSchema);
        if (sv == null) {
            if (!lookupDeletedSchema) {
                // Check if it exists but is deleted
                SchemaValue deletedSv = store.getSchema(subject, versionInt, true);
                if (deletedSv != null && deletedSv.isDeleted()) {
                    throw SchemaRegistryException.schemaVersionSoftDeletedException(subject, version);
                }
            }
            throw SchemaRegistryException.versionNotFoundException(versionInt);
        }

        return toSchemaEntity(sv);
    }

    @Override
    public String getRawSchemaByVersion(String subject, String version) {
        Schema schema = getSchemaByVersion(subject, version, false);
        return schema.getSchema();
    }

    @Override
    public List<Integer> getReferencedBy(String subject, String version) {
        int versionInt = resolveVersion(subject, version, false);
        if (!store.hasSubject(subject, false)) {
            throw SchemaRegistryException.subjectNotFoundException(subject);
        }
        SchemaValue sv = store.getSchema(subject, versionInt, false);
        if (sv == null) {
            throw SchemaRegistryException.versionNotFoundException(versionInt);
        }
        return store.getReferencedBy(subject, versionInt);
    }

    // --- Register ---

    @Override
    public int registerSchema(String subject, RegisterSchemaRequest request, boolean normalize) {
        validateSubject(subject);

        String schemaType = request.getSchemaType() != null ? request.getSchemaType() : "AVRO";
        String schemaString = request.getSchema();
        List<SchemaReference> references = request.getReferences();

        // Parse schema first (validate before checking mode, matching Confluent behavior)
        ParsedSchema parsedSchema = compatibilityService.parseSchema(
            schemaType, schemaString, references, normalize);

        // Check mode
        String mode = store.getInMemoryStore().getEffectiveMode(subject);
        if ("READONLY".equals(mode)) {
            throw SchemaRegistryException.operationNotPermittedException(
                "Subject " + subject + " is in read-only mode");
        }

        String canonicalString = normalize ? parsedSchema.canonicalString() : schemaString;

        // Content-addressed dedup: check if identical schema already exists
        SchemaValue existing = store.lookupSchemaByContent(
            subject, canonicalString, schemaType, references, true);
        if (existing != null && !existing.isDeleted()) {
            return existing.getId();
        }

        // If request specifies an ID, use it
        Integer requestedId = request.getId();
        if (requestedId != null && requestedId > 0) {
            // Check that there's no conflicting schema with this ID
            SchemaValue existingById = store.getSchemaById(requestedId);
            if (existingById != null && !canonicalString.equals(existingById.getSchema())) {
                throw SchemaRegistryException.idDoesNotMatchException(
                    "Schema already registered with id " + requestedId + " is not identical to the schema being registered");
            }
        }

        // Compatibility check
        CompatibilityLevel compatLevel = store.getInMemoryStore().getEffectiveCompatibilityLevel(subject);
        if (compatLevel != CompatibilityLevel.NONE) {
            List<SchemaValue> previousSchemas = store.getSchemasBySubject(subject, false);
            if (!previousSchemas.isEmpty()) {
                List<ParsedSchema> parsedPrevious = new ArrayList<>();
                for (SchemaValue prev : previousSchemas) {
                    try {
                        ParsedSchema parsed = compatibilityService.parseSchema(
                            prev.getSchemaType(), prev.getSchema(), prev.getReferences(), false);
                        parsedPrevious.add(parsed);
                    } catch (Exception e) {
                        log.warn("Could not parse previous schema version {} for {}", prev.getVersion(), subject, e);
                    }
                }
                List<String> incompatibilities = compatibilityService.testCompatibility(
                    compatLevel, parsedSchema, parsedPrevious);
                if (!incompatibilities.isEmpty()) {
                    String msg = String.join("; ", incompatibilities);
                    throw SchemaRegistryException.incompatibleSchemaException(msg);
                }
            }
        }

        // Assign ID
        int id;
        if (requestedId != null && requestedId > 0) {
            id = requestedId;
            // Update max ID if necessary
            store.getInMemoryStore().getMaxSchemaId(); // just read
        } else if (existing != null && existing.isDeleted()) {
            // Reuse the existing ID if the schema was deleted and re-registered
            id = existing.getId();
        } else {
            // Check if this same schema content is registered under a different subject
            id = findExistingSchemaId(canonicalString, schemaType, references);
            if (id < 0) {
                id = store.getInMemoryStore().nextSchemaId();
            }
        }

        // Determine next version
        int latestVersion = store.getLatestVersion(subject, true);
        int newVersion = Math.max(1, latestVersion + 1);

        // Write to Kafka
        SchemaValue schemaValue = new SchemaValue(
            subject, newVersion, id, null, schemaType,
            references, null, null, canonicalString, false
        );
        store.registerSchema(schemaValue);

        return id;
    }

    private int findExistingSchemaId(String schema, String schemaType, List<SchemaReference> references) {
        // Check all subjects for a matching schema content to reuse the ID
        for (String subject : store.getSubjects(true)) {
            SchemaValue match = store.lookupSchemaByContent(subject, schema, schemaType, references, true);
            if (match != null) {
                return match.getId();
            }
        }
        return -1;
    }

    // --- Lookup ---

    @Override
    public Schema lookupSchema(String subject, RegisterSchemaRequest request, boolean normalize,
                               boolean lookupDeletedSchema) {
        if (!store.hasSubject(subject, true)) {
            throw SchemaRegistryException.subjectNotFoundException(subject);
        }
        if (!lookupDeletedSchema && !store.hasSubject(subject, false)) {
            throw SchemaRegistryException.subjectNotFoundException(subject);
        }

        String schemaType = request.getSchemaType() != null ? request.getSchemaType() : "AVRO";
        String schemaString = request.getSchema();
        List<SchemaReference> references = request.getReferences();

        if (normalize) {
            ParsedSchema parsed = compatibilityService.parseSchema(schemaType, schemaString, references, true);
            schemaString = parsed.canonicalString();
        }

        SchemaValue sv = store.lookupSchemaByContent(subject, schemaString, schemaType, references, lookupDeletedSchema);
        if (sv == null) {
            throw SchemaRegistryException.schemaNotFoundException();
        }

        return toSchemaEntity(sv);
    }

    // --- Delete ---

    @Override
    public List<Integer> deleteSubject(String subject, boolean permanent) {
        if (!store.hasSubject(subject, true)) {
            throw SchemaRegistryException.subjectNotFoundException(subject);
        }

        String mode = store.getInMemoryStore().getEffectiveMode(subject);
        if ("READONLY".equals(mode)) {
            throw SchemaRegistryException.operationNotPermittedException(
                "Subject " + subject + " is in read-only mode");
        }

        if (permanent) {
            // Must be soft-deleted first
            if (!store.getInMemoryStore().isSubjectSoftDeleted(subject)) {
                throw SchemaRegistryException.subjectNotSoftDeletedException(subject);
            }

            List<Integer> versions = store.getVersions(subject, true);
            store.hardDeleteSubject(subject);
            return versions;
        } else {
            // Soft delete
            if (!store.hasSubject(subject, false)) {
                throw SchemaRegistryException.subjectSoftDeletedException(subject);
            }

            // Check for references
            List<Integer> versions = store.getVersions(subject, false);
            store.softDeleteSubject(subject);
            return versions;
        }
    }

    @Override
    public int deleteSchemaVersion(String subject, String version, boolean permanent) {
        if (!store.hasSubject(subject, true)) {
            throw SchemaRegistryException.subjectNotFoundException(subject);
        }

        String mode = store.getInMemoryStore().getEffectiveMode(subject);
        if ("READONLY".equals(mode)) {
            throw SchemaRegistryException.operationNotPermittedException(
                "Subject " + subject + " is in read-only mode");
        }

        int versionInt = resolveVersion(subject, version, true);

        SchemaValue sv = store.getSchema(subject, versionInt, true);
        if (sv == null) {
            throw SchemaRegistryException.versionNotFoundException(versionInt);
        }

        if (permanent) {
            if (!sv.isDeleted()) {
                throw SchemaRegistryException.schemaVersionNotSoftDeletedException(subject, String.valueOf(versionInt));
            }
            store.hardDeleteSchema(subject, versionInt);
        } else {
            if (sv.isDeleted()) {
                throw SchemaRegistryException.schemaVersionSoftDeletedException(subject, String.valueOf(versionInt));
            }
            // Check references
            List<Integer> refs = store.getReferencedBy(subject, versionInt);
            if (!refs.isEmpty()) {
                throw SchemaRegistryException.referenceExistsException(
                    "One or more references exist to the schema {magic=1,keytype=SCHEMA,subject=" + subject
                        + ",version=" + versionInt + "}.");
            }
            store.softDeleteSchema(subject, versionInt);
        }

        return versionInt;
    }

    // --- Compatibility ---

    @Override
    public List<String> testCompatibility(String subject, String version, RegisterSchemaRequest request,
                                          boolean verbose) {
        String schemaType = request.getSchemaType() != null ? request.getSchemaType() : "AVRO";
        String schemaString = request.getSchema();
        List<SchemaReference> references = request.getReferences();

        ParsedSchema parsedSchema = compatibilityService.parseSchema(schemaType, schemaString, references, false);

        List<ParsedSchema> previousSchemas = new ArrayList<>();
        if ("latest".equals(version)) {
            int latestVer = store.getLatestVersion(subject, false);
            if (latestVer > 0) {
                SchemaValue sv = store.getSchema(subject, latestVer, false);
                if (sv != null) {
                    previousSchemas.add(compatibilityService.parseSchema(
                        sv.getSchemaType(), sv.getSchema(), sv.getReferences(), false));
                }
            }
        } else {
            int versionInt = parseVersionId(version);
            SchemaValue sv = store.getSchema(subject, versionInt, false);
            if (sv == null) {
                throw SchemaRegistryException.versionNotFoundException(versionInt);
            }
            previousSchemas.add(compatibilityService.parseSchema(
                sv.getSchemaType(), sv.getSchema(), sv.getReferences(), false));
        }

        CompatibilityLevel level = store.getInMemoryStore().getEffectiveCompatibilityLevel(subject);
        return compatibilityService.testCompatibility(level, parsedSchema, previousSchemas);
    }

    @Override
    public boolean isCompatible(String subject, String version, RegisterSchemaRequest request) {
        List<String> incompatibilities = testCompatibility(subject, version, request, false);
        return incompatibilities.isEmpty();
    }

    // --- Config ---

    @Override
    public Map<String, String> getGlobalConfig() {
        Map<String, String> config = new LinkedHashMap<>();
        config.put("compatibilityLevel", store.getGlobalCompatibilityLevel().getName());
        return config;
    }

    @Override
    public Map<String, String> setGlobalConfig(CompatibilityLevel level) {
        store.setGlobalCompatibilityLevel(level);
        Map<String, String> config = new LinkedHashMap<>();
        config.put("compatibility", level.getName());
        return config;
    }

    @Override
    public Map<String, String> getSubjectConfig(String subject, boolean defaultToGlobal) {
        if (!defaultToGlobal && !store.hasSubjectCompatibilityLevel(subject)) {
            throw SchemaRegistryException.subjectLevelCompatibilityNotConfigured(subject);
        }
        CompatibilityLevel level = store.getSubjectCompatibilityLevel(subject);
        if (level == null && defaultToGlobal) {
            level = store.getGlobalCompatibilityLevel();
        }
        if (level == null) {
            throw SchemaRegistryException.subjectLevelCompatibilityNotConfigured(subject);
        }
        Map<String, String> config = new LinkedHashMap<>();
        config.put("compatibilityLevel", level.getName());
        return config;
    }

    @Override
    public Map<String, String> setSubjectConfig(String subject, CompatibilityLevel level) {
        store.setSubjectCompatibilityLevel(subject, level);
        Map<String, String> config = new LinkedHashMap<>();
        config.put("compatibility", level.getName());
        return config;
    }

    @Override
    public Map<String, String> deleteSubjectConfig(String subject) {
        CompatibilityLevel level = store.getSubjectCompatibilityLevel(subject);
        if (level == null) {
            throw SchemaRegistryException.subjectLevelCompatibilityNotConfigured(subject);
        }
        store.deleteSubjectCompatibilityLevel(subject);
        Map<String, String> config = new LinkedHashMap<>();
        config.put("compatibilityLevel", level.getName());
        return config;
    }

    // --- Mode ---

    @Override
    public Map<String, String> getGlobalMode() {
        Map<String, String> result = new LinkedHashMap<>();
        result.put("mode", store.getGlobalMode());
        return result;
    }

    @Override
    public Map<String, String> setGlobalMode(String mode, boolean force) {
        validateMode(mode);
        store.setGlobalMode(mode);
        Map<String, String> result = new LinkedHashMap<>();
        result.put("mode", mode);
        return result;
    }

    @Override
    public Map<String, String> getSubjectMode(String subject, boolean defaultToGlobal) {
        if (!defaultToGlobal && !store.hasSubjectMode(subject)) {
            throw SchemaRegistryException.subjectLevelModeNotConfigured(subject);
        }
        String mode = store.getSubjectMode(subject);
        if (mode == null && defaultToGlobal) {
            mode = store.getGlobalMode();
        }
        if (mode == null) {
            throw SchemaRegistryException.subjectLevelModeNotConfigured(subject);
        }
        Map<String, String> result = new LinkedHashMap<>();
        result.put("mode", mode);
        return result;
    }

    @Override
    public Map<String, String> setSubjectMode(String subject, String mode, boolean force) {
        validateMode(mode);
        if ("IMPORT".equalsIgnoreCase(mode) && !force) {
            List<String> subjects = store.getSubjects(false);
            if (!subjects.isEmpty()) {
                throw SchemaRegistryException.operationNotPermittedException(
                    "Cannot import since found existing subjects");
            }
        }
        store.setSubjectMode(subject, mode);
        Map<String, String> result = new LinkedHashMap<>();
        result.put("mode", mode);
        return result;
    }

    @Override
    public Map<String, String> deleteSubjectMode(String subject) {
        String mode = store.getSubjectMode(subject);
        if (mode == null) {
            throw SchemaRegistryException.subjectNotFoundException(subject);
        }
        store.deleteSubjectMode(subject);
        Map<String, String> result = new LinkedHashMap<>();
        result.put("mode", mode);
        return result;
    }

    // --- Helpers ---

    private int resolveVersion(String subject, String version, boolean lookupDeletedSchema) {
        if ("latest".equalsIgnoreCase(version) || "-1".equals(version)) {
            int latest = store.getLatestVersion(subject, lookupDeletedSchema);
            if (latest < 0) {
                throw SchemaRegistryException.versionNotFoundException(-1);
            }
            return latest;
        }
        if ("-2".equals(version)) {
            // Earliest version
            List<Integer> versions = store.getVersions(subject, lookupDeletedSchema);
            if (versions.isEmpty()) {
                throw SchemaRegistryException.versionNotFoundException(-2);
            }
            return versions.get(0);
        }
        return parseVersionId(version);
    }

    private int parseVersionId(String version) {
        try {
            int v = Integer.parseInt(version);
            if (v < 1) {
                throw SchemaRegistryException.invalidVersionException(version);
            }
            return v;
        } catch (NumberFormatException e) {
            throw SchemaRegistryException.invalidVersionException(version);
        }
    }

    private void validateSubject(String subject) {
        if (subject == null || subject.isEmpty() || subject.contains("\0")) {
            throw SchemaRegistryException.invalidSubjectException(subject != null ? subject : "null");
        }
    }

    private void validateMode(String mode) {
        if (mode == null) {
            throw SchemaRegistryException.invalidModeException(null);
        }
        switch (mode.toUpperCase()) {
            case "READWRITE":
            case "READONLY":
            case "READONLY_OVERRIDE":
            case "IMPORT":
                break;
            default:
                throw SchemaRegistryException.invalidModeException(mode);
        }
    }

    private Schema toSchemaEntity(SchemaValue sv) {
        Schema schema = new Schema(
            sv.getSubject(),
            sv.getVersion(),
            sv.getId(),
            sv.getSchemaType(),
            sv.getReferences(),
            sv.getSchema()
        );
        return schema;
    }
}
