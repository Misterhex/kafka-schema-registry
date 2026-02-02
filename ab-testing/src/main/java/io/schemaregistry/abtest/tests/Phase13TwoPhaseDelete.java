package io.schemaregistry.abtest.tests;

import io.schemaregistry.abtest.comparator.ComparisonMode;
import io.schemaregistry.abtest.comparator.ResponseComparator;
import io.schemaregistry.abtest.runner.HttpExecutor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(13)
public class Phase13TwoPhaseDelete extends AbstractTestPhase {

    static final String AVRO_DEL_V1 = """
            {"schema":"{\\"type\\":\\"record\\",\\"name\\":\\"DelTest\\",\\"namespace\\":\\"com.example\\",\\"fields\\":[{\\"name\\":\\"a\\",\\"type\\":\\"string\\"}]}"}""";

    static final String AVRO_DEL_V2 = """
            {"schema":"{\\"type\\":\\"record\\",\\"name\\":\\"DelTest\\",\\"namespace\\":\\"com.example\\",\\"fields\\":[{\\"name\\":\\"a\\",\\"type\\":\\"string\\"},{\\"name\\":\\"b\\",\\"type\\":[\\"null\\",\\"string\\"],\\"default\\":null}]}"}""";

    public Phase13TwoPhaseDelete(HttpExecutor httpExecutor, ResponseComparator comparator) {
        super(httpExecutor, comparator);
    }

    @Override
    public String name() {
        return "Phase 13: Two-Phase Delete Lifecycle";
    }

    @Override
    protected void runTests() {
        // Setup: register a subject with two versions
        test("Setup: register del-test v1", "POST", "/subjects/del-test-value/versions", AVRO_DEL_V1, ComparisonMode.EXACT);
        test("Setup: register del-test v2", "POST", "/subjects/del-test-value/versions", AVRO_DEL_V2, ComparisonMode.EXACT);

        // --- Version-level two-phase delete ---

        // Soft delete version 1
        test("Soft delete version 1", "DELETE", "/subjects/del-test-value/versions/1", null, ComparisonMode.EXACT);

        // Verify version 1 is not visible without deleted=true
        test("Get soft-deleted version 1 (40406)", "GET", "/subjects/del-test-value/versions/1", null,
                ComparisonMode.STATUS_AND_ERROR_CODE);

        // Verify versions list excludes soft-deleted
        test("Versions list excludes soft-deleted", "GET", "/subjects/del-test-value/versions", null, ComparisonMode.EXACT);

        // Verify version 1 IS visible with deleted=true
        test("Get soft-deleted version with deleted=true", "GET", "/subjects/del-test-value/versions/1?deleted=true", null,
                ComparisonMode.JSON_STRUCTURE, new String[]{"subject", "version", "id", "schemaType"});

        // Versions list with deleted=true includes soft-deleted
        test("Versions with deleted=true includes all", "GET", "/subjects/del-test-value/versions?deleted=true", null,
                ComparisonMode.EXACT);

        // Cannot soft-delete again (already deleted) - should get error
        test("Re-soft-delete version 1 fails", "DELETE", "/subjects/del-test-value/versions/1", null,
                ComparisonMode.STATUS_AND_ERROR_CODE);

        // Cannot permanent-delete version 2 without soft-delete first (40407)
        test("Permanent delete non-soft-deleted version (40407)", "DELETE",
                "/subjects/del-test-value/versions/2?permanent=true", null,
                ComparisonMode.STATUS_AND_ERROR_CODE);

        // Hard delete version 1
        test("Permanent delete version 1", "DELETE", "/subjects/del-test-value/versions/1?permanent=true", null,
                ComparisonMode.EXACT);

        // --- Subject-level two-phase delete ---

        // Soft delete subject
        test("Soft delete subject del-test-value", "DELETE", "/subjects/del-test-value", null, ComparisonMode.SET);

        // Subject should not appear in subjects list
        test("Subjects list excludes soft-deleted", "GET", "/subjects", null, ComparisonMode.SET);

        // Subject appears with deleted=true
        test("Subjects with deleted=true includes soft-deleted", "GET", "/subjects?deleted=true", null, ComparisonMode.SET);

        // Lookup schema under soft-deleted subject with deleted=true
        test("Lookup schema in soft-deleted subject with deleted=true", "POST",
                "/subjects/del-test-value?deleted=true", AVRO_DEL_V2,
                ComparisonMode.JSON_STRUCTURE, new String[]{"subject", "version", "id"});

        // Re-soft-delete fails (40404)
        test("Re-soft-delete subject fails (40404)", "DELETE", "/subjects/del-test-value", null,
                ComparisonMode.STATUS_AND_ERROR_CODE);

        // Permanent delete without soft-delete first on a new subject
        // First register a quick subject
        test("Setup: register nodelete-test v1", "POST", "/subjects/nodelete-test-value/versions", AVRO_DEL_V1,
                ComparisonMode.EXACT);

        // Permanent delete without soft-delete (40405)
        test("Permanent delete non-soft-deleted subject (40405)", "DELETE",
                "/subjects/nodelete-test-value?permanent=true", null,
                ComparisonMode.STATUS_AND_ERROR_CODE);

        // Permanent delete the soft-deleted subject
        test("Permanent delete del-test-value", "DELETE", "/subjects/del-test-value?permanent=true", null,
                ComparisonMode.SET);

        // Clean up nodelete-test-value
        test("Cleanup: soft delete nodelete-test-value", "DELETE", "/subjects/nodelete-test-value", null,
                ComparisonMode.SET);
        test("Cleanup: permanent delete nodelete-test-value", "DELETE",
                "/subjects/nodelete-test-value?permanent=true", null, ComparisonMode.SET);
    }
}
