package io.schemaregistry.abtest.tests;

import io.schemaregistry.abtest.comparator.ComparisonMode;
import io.schemaregistry.abtest.comparator.ResponseComparator;
import io.schemaregistry.abtest.runner.HttpExecutor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Subject-level configuration overrides — Confluent SR allows per-subject
 * compatibility levels and per-subject mode that override the global
 * setting. Phase 6 only checked the global level; this phase rounds out
 * the subject-level CRUD path.
 */
@Component
@Order(16)
public class Phase16SubjectLevelConfig extends AbstractTestPhase {

    static final String FORWARD = "{\"compatibility\":\"FORWARD\"}";
    static final String FULL = "{\"compatibility\":\"FULL_TRANSITIVE\"}";
    static final String READONLY = "{\"mode\":\"READONLY\"}";
    static final String READWRITE = "{\"mode\":\"READWRITE\"}";

    static final String SCHEMA = """
            {"schema":"{\\"type\\":\\"record\\",\\"name\\":\\"S16\\",\\"fields\\":[{\\"name\\":\\"x\\",\\"type\\":\\"int\\"}]}"}""";

    public Phase16SubjectLevelConfig(HttpExecutor httpExecutor, ResponseComparator comparator) {
        super(httpExecutor, comparator);
    }

    @Override
    public String name() {
        return "Phase 16: Subject-level Config & Mode";
    }

    @Override
    protected void runTests() {
        test("Bootstrap subject", "POST", "/subjects/cfg16-ab-test/versions", SCHEMA,
                ComparisonMode.JSON_STRUCTURE, new String[]{"id"});

        test("Get subject config (initially 404)", "GET", "/config/cfg16-ab-test", null,
                ComparisonMode.STATUS_AND_ERROR_CODE);
        test("Set subject compat=FORWARD", "PUT", "/config/cfg16-ab-test", FORWARD,
                ComparisonMode.JSON_STRUCTURE, new String[]{"compatibility"});
        test("Get subject config now reports FORWARD", "GET", "/config/cfg16-ab-test", null,
                ComparisonMode.JSON_STRUCTURE, new String[]{"compatibilityLevel"});
        test("Update subject compat=FULL_TRANSITIVE", "PUT", "/config/cfg16-ab-test", FULL,
                ComparisonMode.JSON_STRUCTURE, new String[]{"compatibility"});
        test("Delete subject config", "DELETE", "/config/cfg16-ab-test", null,
                ComparisonMode.JSON_STRUCTURE, new String[]{"compatibility"});
        test("Get subject config after delete (404)", "GET", "/config/cfg16-ab-test", null,
                ComparisonMode.STATUS_AND_ERROR_CODE);

        test("Set subject mode=READONLY", "PUT", "/mode/cfg16-ab-test", READONLY,
                ComparisonMode.JSON_STRUCTURE, new String[]{"mode"});
        test("Get subject mode reports READONLY", "GET", "/mode/cfg16-ab-test", null,
                ComparisonMode.JSON_STRUCTURE, new String[]{"mode"});
        test("Set subject mode=READWRITE", "PUT", "/mode/cfg16-ab-test", READWRITE,
                ComparisonMode.JSON_STRUCTURE, new String[]{"mode"});
    }
}
