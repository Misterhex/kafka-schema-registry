package io.schemaregistry.mirror.storage;

import io.confluent.kafka.schemaregistry.client.rest.entities.SchemaReference;
import io.schemaregistry.mirror.schema.CompatibilityLevel;
import io.schemaregistry.mirror.storage.model.SchemaValue;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class InMemoryStore {

    // subject -> (version -> SchemaValue)
    private final ConcurrentHashMap<String, ConcurrentSkipListMap<Integer, SchemaValue>> schemas = new ConcurrentHashMap<>();

    // id -> SchemaValue (first registration wins)
    private final ConcurrentHashMap<Integer, SchemaValue> schemasById = new ConcurrentHashMap<>();

    // subject -> CompatibilityLevel
    private final ConcurrentHashMap<String, CompatibilityLevel> subjectCompatibility = new ConcurrentHashMap<>();

    // Global compatibility level
    private volatile CompatibilityLevel globalCompatibility = CompatibilityLevel.BACKWARD;

    // subject -> mode
    private final ConcurrentHashMap<String, String> subjectModes = new ConcurrentHashMap<>();

    // Global mode
    private volatile String globalMode = "READWRITE";

    // Set of soft-deleted subjects
    private final ConcurrentHashMap<String, Boolean> softDeletedSubjects = new ConcurrentHashMap<>();

    // Max schema ID
    private final AtomicInteger maxId = new AtomicInteger(0);

    // ---- Schema operations ----

    public void put(SchemaValue value) {
        if (value == null) return;

        String subject = value.getSubject();
        int version = value.getVersion();
        int id = value.getId();

        schemas.computeIfAbsent(subject, k -> new ConcurrentSkipListMap<>()).put(version, value);
        schemasById.putIfAbsent(id, value);

        int currentMax;
        do {
            currentMax = maxId.get();
            if (id <= currentMax) break;
        } while (!maxId.compareAndSet(currentMax, id));

        // Track soft deleted state
        if (value.isDeleted()) {
            // Check if all versions are deleted
            ConcurrentSkipListMap<Integer, SchemaValue> versions = schemas.get(subject);
            if (versions != null && versions.values().stream().allMatch(SchemaValue::isDeleted)) {
                softDeletedSubjects.put(subject, true);
            }
        } else {
            softDeletedSubjects.remove(subject);
        }
    }

    public void markDeleted(String subject, int version) {
        ConcurrentSkipListMap<Integer, SchemaValue> versions = schemas.get(subject);
        if (versions != null) {
            SchemaValue sv = versions.get(version);
            if (sv != null) {
                sv.setDeleted(true);
            }
            if (versions.values().stream().allMatch(SchemaValue::isDeleted)) {
                softDeletedSubjects.put(subject, true);
            }
        }
    }

    public void hardDelete(String subject, int version) {
        ConcurrentSkipListMap<Integer, SchemaValue> versions = schemas.get(subject);
        if (versions != null) {
            versions.remove(version);
            if (versions.isEmpty()) {
                schemas.remove(subject);
                softDeletedSubjects.remove(subject);
            }
        }
    }

    public void softDeleteSubject(String subject) {
        ConcurrentSkipListMap<Integer, SchemaValue> versions = schemas.get(subject);
        if (versions != null) {
            versions.values().forEach(sv -> sv.setDeleted(true));
            softDeletedSubjects.put(subject, true);
        }
    }

    public void hardDeleteSubject(String subject) {
        schemas.remove(subject);
        softDeletedSubjects.remove(subject);
        subjectCompatibility.remove(subject);
        subjectModes.remove(subject);
    }

    public SchemaValue getSchemaById(int id) {
        return schemasById.get(id);
    }

    public SchemaValue getSchema(String subject, int version, boolean lookupDeletedSchema) {
        ConcurrentSkipListMap<Integer, SchemaValue> versions = schemas.get(subject);
        if (versions == null) return null;
        SchemaValue sv = versions.get(version);
        if (sv == null) return null;
        if (!lookupDeletedSchema && sv.isDeleted()) return null;
        return sv;
    }

    public List<Integer> getVersions(String subject, boolean lookupDeletedSchema) {
        ConcurrentSkipListMap<Integer, SchemaValue> versions = schemas.get(subject);
        if (versions == null) return Collections.emptyList();
        if (lookupDeletedSchema) {
            return new ArrayList<>(versions.keySet());
        }
        return versions.entrySet().stream()
            .filter(e -> !e.getValue().isDeleted())
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());
    }

    public List<String> getSubjects(boolean lookupDeletedSubjects) {
        if (lookupDeletedSubjects) {
            return schemas.keySet().stream().sorted().collect(Collectors.toList());
        }
        return schemas.entrySet().stream()
            .filter(e -> e.getValue().values().stream().anyMatch(sv -> !sv.isDeleted()))
            .map(Map.Entry::getKey)
            .sorted()
            .collect(Collectors.toList());
    }

    public int getLatestVersion(String subject, boolean lookupDeletedSchema) {
        ConcurrentSkipListMap<Integer, SchemaValue> versions = schemas.get(subject);
        if (versions == null || versions.isEmpty()) return -1;
        if (lookupDeletedSchema) {
            return versions.lastKey();
        }
        return versions.descendingMap().entrySet().stream()
            .filter(e -> !e.getValue().isDeleted())
            .map(Map.Entry::getKey)
            .findFirst()
            .orElse(-1);
    }

    public boolean hasSubject(String subject, boolean lookupDeletedSubjects) {
        ConcurrentSkipListMap<Integer, SchemaValue> versions = schemas.get(subject);
        if (versions == null || versions.isEmpty()) return false;
        if (lookupDeletedSubjects) return true;
        return versions.values().stream().anyMatch(sv -> !sv.isDeleted());
    }

    public boolean isSubjectSoftDeleted(String subject) {
        return softDeletedSubjects.containsKey(subject);
    }

    public List<SchemaValue> getSchemasBySubject(String subject, boolean lookupDeletedSchema) {
        ConcurrentSkipListMap<Integer, SchemaValue> versions = schemas.get(subject);
        if (versions == null) return Collections.emptyList();
        if (lookupDeletedSchema) {
            return new ArrayList<>(versions.values());
        }
        return versions.values().stream()
            .filter(sv -> !sv.isDeleted())
            .collect(Collectors.toList());
    }

    public SchemaValue lookupSchemaByContent(String subject, String schema, String schemaType,
                                             List<SchemaReference> references, boolean lookupDeletedSchema) {
        String type = schemaType != null ? schemaType : "AVRO";
        ConcurrentSkipListMap<Integer, SchemaValue> versions = schemas.get(subject);
        if (versions == null) return null;

        for (SchemaValue sv : versions.values()) {
            if (!lookupDeletedSchema && sv.isDeleted()) continue;
            if (type.equals(sv.getSchemaType()) && schema.equals(sv.getSchema())) {
                // Check references match
                List<SchemaReference> svRefs = sv.getReferences();
                if (referencesMatch(references, svRefs)) {
                    return sv;
                }
            }
        }
        return null;
    }

    private boolean referencesMatch(List<SchemaReference> refs1, List<SchemaReference> refs2) {
        List<SchemaReference> r1 = refs1 != null ? refs1 : Collections.emptyList();
        List<SchemaReference> r2 = refs2 != null ? refs2 : Collections.emptyList();
        if (r1.size() != r2.size()) return false;
        for (int i = 0; i < r1.size(); i++) {
            SchemaReference a = r1.get(i);
            SchemaReference b = r2.get(i);
            if (!Objects.equals(a.getName(), b.getName()) ||
                !Objects.equals(a.getSubject(), b.getSubject()) ||
                a.getVersion() != b.getVersion()) {
                return false;
            }
        }
        return true;
    }

    public List<String> getSubjectsForSchemaId(int id, boolean lookupDeletedSubjects) {
        List<String> result = new ArrayList<>();
        for (Map.Entry<String, ConcurrentSkipListMap<Integer, SchemaValue>> entry : schemas.entrySet()) {
            for (SchemaValue sv : entry.getValue().values()) {
                if (sv.getId() == id) {
                    if (lookupDeletedSubjects || !sv.isDeleted()) {
                        result.add(entry.getKey());
                        break;
                    }
                }
            }
        }
        Collections.sort(result);
        return result;
    }

    public List<Map<String, Object>> getVersionsForSchemaId(int id, boolean lookupDeletedSubjects) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<String, ConcurrentSkipListMap<Integer, SchemaValue>> entry : schemas.entrySet()) {
            for (SchemaValue sv : entry.getValue().values()) {
                if (sv.getId() == id) {
                    if (lookupDeletedSubjects || !sv.isDeleted()) {
                        Map<String, Object> subjectVersion = new LinkedHashMap<>();
                        subjectVersion.put("subject", entry.getKey());
                        subjectVersion.put("version", sv.getVersion());
                        result.add(subjectVersion);
                    }
                }
            }
        }
        return result;
    }

    public List<Integer> getReferencedBy(String subject, int version) {
        SchemaValue target = getSchema(subject, version, true);
        if (target == null) return Collections.emptyList();

        List<Integer> result = new ArrayList<>();
        for (Map.Entry<String, ConcurrentSkipListMap<Integer, SchemaValue>> entry : schemas.entrySet()) {
            for (SchemaValue sv : entry.getValue().values()) {
                if (sv.getReferences() != null) {
                    for (SchemaReference ref : sv.getReferences()) {
                        if (subject.equals(ref.getSubject()) && version == ref.getVersion()) {
                            if (!sv.isDeleted()) {
                                result.add(sv.getId());
                            }
                        }
                    }
                }
            }
        }
        return result;
    }

    public int getMaxSchemaId() {
        return maxId.get();
    }

    public int nextSchemaId() {
        return maxId.incrementAndGet();
    }

    // All schemas for listing (GET /schemas)
    public List<SchemaValue> getAllSchemas(String subjectPrefix, boolean lookupDeletedSchemas, Integer limit, Integer offset) {
        List<SchemaValue> all = new ArrayList<>();
        for (Map.Entry<String, ConcurrentSkipListMap<Integer, SchemaValue>> entry : schemas.entrySet()) {
            if (subjectPrefix != null && !entry.getKey().startsWith(subjectPrefix)) continue;
            for (SchemaValue sv : entry.getValue().values()) {
                if (!lookupDeletedSchemas && sv.isDeleted()) continue;
                all.add(sv);
            }
        }
        // Sort by ID
        all.sort(Comparator.comparingInt(SchemaValue::getId));
        // Apply offset/limit
        int start = offset != null ? offset : 0;
        int end = limit != null ? Math.min(start + limit, all.size()) : all.size();
        if (start >= all.size()) return Collections.emptyList();
        return all.subList(start, end);
    }

    // ---- Config operations ----

    public CompatibilityLevel getGlobalCompatibilityLevel() {
        return globalCompatibility;
    }

    public void setGlobalCompatibilityLevel(CompatibilityLevel level) {
        this.globalCompatibility = level;
    }

    public CompatibilityLevel getSubjectCompatibilityLevel(String subject) {
        return subjectCompatibility.get(subject);
    }

    public void setSubjectCompatibilityLevel(String subject, CompatibilityLevel level) {
        subjectCompatibility.put(subject, level);
    }

    public void deleteSubjectCompatibilityLevel(String subject) {
        subjectCompatibility.remove(subject);
    }

    public boolean hasSubjectCompatibilityLevel(String subject) {
        return subjectCompatibility.containsKey(subject);
    }

    public CompatibilityLevel getEffectiveCompatibilityLevel(String subject) {
        CompatibilityLevel subjectLevel = subjectCompatibility.get(subject);
        return subjectLevel != null ? subjectLevel : globalCompatibility;
    }

    // ---- Mode operations ----

    public String getGlobalMode() {
        return globalMode;
    }

    public void setGlobalMode(String mode) {
        this.globalMode = mode;
    }

    public String getSubjectMode(String subject) {
        return subjectModes.get(subject);
    }

    public void setSubjectMode(String subject, String mode) {
        subjectModes.put(subject, mode);
    }

    public void deleteSubjectMode(String subject) {
        subjectModes.remove(subject);
    }

    public boolean hasSubjectMode(String subject) {
        return subjectModes.containsKey(subject);
    }

    public String getEffectiveMode(String subject) {
        String mode = subjectModes.get(subject);
        return mode != null ? mode : globalMode;
    }
}
