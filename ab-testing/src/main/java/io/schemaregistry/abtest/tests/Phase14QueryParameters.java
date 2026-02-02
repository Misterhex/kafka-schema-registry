package io.schemaregistry.abtest.tests;

import io.schemaregistry.abtest.comparator.ComparisonMode;
import io.schemaregistry.abtest.comparator.ResponseComparator;
import io.schemaregistry.abtest.runner.HttpExecutor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(14)
public class Phase14QueryParameters extends AbstractTestPhase {

    static final String AVRO_QP = """
            {"schema":"{\\"type\\":\\"record\\",\\"name\\":\\"QpTest\\",\\"namespace\\":\\"com.example\\",\\"fields\\":[{\\"name\\":\\"x\\",\\"type\\":\\"string\\"}]}"}""";

    static final String AVRO_QP_V2 = """
            {"schema":"{\\"type\\":\\"record\\",\\"name\\":\\"QpTest\\",\\"namespace\\":\\"com.example\\",\\"fields\\":[{\\"name\\":\\"x\\",\\"type\\":\\"string\\"},{\\"name\\":\\"y\\",\\"type\\":[\\"null\\",\\"string\\"],\\"default\\":null}]}"}""";

    static final String COMPAT_SCHEMA = """
            {"schema":"{\\"type\\":\\"record\\",\\"name\\":\\"QpTest\\",\\"namespace\\":\\"com.example\\",\\"fields\\":[{\\"name\\":\\"x\\",\\"type\\":\\"string\\"},{\\"name\\":\\"y\\",\\"type\\":[\\"null\\",\\"string\\"],\\"default\\":null},{\\"name\\":\\"z\\",\\"type\\":[\\"null\\",\\"int\\"],\\"default\\":null}]}"}""";

    static final String INCOMPAT_SCHEMA = """
            {"schema":"{\\"type\\":\\"record\\",\\"name\\":\\"QpTest\\",\\"namespace\\":\\"com.example\\",\\"fields\\":[{\\"name\\":\\"x\\",\\"type\\":\\"int\\"}]}"}""";

    public Phase14QueryParameters(HttpExecutor httpExecutor, ResponseComparator comparator) {
        super(httpExecutor, comparator);
    }

    @Override
    public String name() {
        return "Phase 14: Query Parameter Variations";
    }

    @Override
    protected void runTests() {
        // Setup: register schemas for query param testing
        test("Setup: register qp-test v1", "POST", "/subjects/qp-test-value/versions", AVRO_QP, ComparisonMode.EXACT);
        test("Setup: register qp-test v2", "POST", "/subjects/qp-test-value/versions", AVRO_QP_V2, ComparisonMode.EXACT);

        // --- verbose=true on compatibility endpoints ---
        test("Compatibility check verbose=true (compatible)", "POST",
                "/compatibility/subjects/qp-test-value/versions/latest?verbose=true", COMPAT_SCHEMA,
                ComparisonMode.JSON_STRUCTURE, new String[]{"is_compatible"});

        test("Compatibility check verbose=true (incompatible)", "POST",
                "/compatibility/subjects/qp-test-value/versions/latest?verbose=true", INCOMPAT_SCHEMA,
                ComparisonMode.JSON_STRUCTURE, new String[]{"is_compatible"});

        test("Compatibility check all versions verbose=true", "POST",
                "/compatibility/subjects/qp-test-value/versions?verbose=true", INCOMPAT_SCHEMA,
                ComparisonMode.JSON_STRUCTURE, new String[]{"is_compatible"});

        // --- normalize=true ---
        test("Register with normalize=true", "POST",
                "/subjects/qp-test-value/versions?normalize=true", AVRO_QP,
                ComparisonMode.EXACT);

        test("Lookup schema with normalize=true", "POST",
                "/subjects/qp-test-value?normalize=true", AVRO_QP,
                ComparisonMode.JSON_STRUCTURE, new String[]{"subject", "version", "id"});

        // --- subjectPrefix ---
        test("Subjects with subjectPrefix=qp", "GET", "/subjects?subjectPrefix=qp", null, ComparisonMode.SET);
        test("Subjects with subjectPrefix=nonexistent", "GET", "/subjects?subjectPrefix=zzz-nonexistent", null,
                ComparisonMode.EXACT);
        test("Subjects with empty subjectPrefix (all)", "GET", "/subjects?subjectPrefix=", null, ComparisonMode.SET);

        // --- fetchMaxId ---
        test("Get schema with fetchMaxId=true", "GET", "/schemas/ids/2?fetchMaxId=true", null,
                ComparisonMode.JSON_STRUCTURE, new String[]{"schemaType"});

        // --- subject filter on schema ID endpoints ---
        test("Get schema by ID with subject filter", "GET",
                "/schemas/ids/2?subject=ab-test-value", null,
                ComparisonMode.JSON_STRUCTURE, new String[]{"schemaType"});

        test("Get raw schema by ID with subject filter", "GET",
                "/schemas/ids/2/schema?subject=ab-test-value", null,
                ComparisonMode.EXACT);

        // --- defaultToGlobal=false on config (should return 40408 if no subject config) ---
        test("Get config defaultToGlobal=false (no subject config)", "GET",
                "/config/qp-test-value?defaultToGlobal=false", null,
                ComparisonMode.STATUS_AND_ERROR_CODE);

        // Set subject config, then defaultToGlobal=false should work
        test("Set subject config for qp-test-value", "PUT", "/config/qp-test-value",
                "{\"compatibility\":\"FULL\"}", ComparisonMode.EXACT);
        test("Get config defaultToGlobal=false (with subject config)", "GET",
                "/config/qp-test-value?defaultToGlobal=false", null,
                ComparisonMode.EXACT);

        // Clean up subject config
        test("Delete subject config qp-test-value", "DELETE", "/config/qp-test-value", null,
                ComparisonMode.JSON_STRUCTURE, new String[]{"compatibilityLevel"});

        // --- defaultToGlobal on mode ---
        // Mode controller defaults defaultToGlobal=false, so without subject mode it should get 40409
        test("Get mode defaultToGlobal=false (no subject mode)", "GET",
                "/mode/qp-test-value?defaultToGlobal=false", null,
                ComparisonMode.STATUS_AND_ERROR_CODE);

        test("Get mode defaultToGlobal=true (falls back to global)", "GET",
                "/mode/qp-test-value?defaultToGlobal=true", null,
                ComparisonMode.EXACT);

        // --- force=true on mode ---
        test("Set global mode with force=true", "PUT", "/mode?force=true",
                "{\"mode\":\"READWRITE\"}", ComparisonMode.EXACT);

        test("Set subject mode with force=true", "PUT", "/mode/qp-test-value?force=true",
                "{\"mode\":\"READWRITE\"}", ComparisonMode.EXACT);

        // Clean up subject mode
        test("Delete subject mode qp-test-value", "DELETE", "/mode/qp-test-value", null, ComparisonMode.EXACT);

        // --- deleted=true on schema ID endpoints ---
        // Schema ID 2 belongs to ab-test-value which still exists
        test("Subjects for schema ID with deleted=true", "GET",
                "/schemas/ids/2/subjects?deleted=true", null, ComparisonMode.SET);

        test("Versions for schema ID with deleted=true", "GET",
                "/schemas/ids/2/versions?deleted=true", null, ComparisonMode.SET);

        // Clean up: soft delete and permanent delete qp-test-value
        test("Cleanup: soft delete qp-test-value", "DELETE", "/subjects/qp-test-value", null, ComparisonMode.SET);
        test("Cleanup: permanent delete qp-test-value", "DELETE", "/subjects/qp-test-value?permanent=true", null,
                ComparisonMode.SET);
    }
}
