package io.schemaregistry.abtest.tests;

import io.schemaregistry.abtest.comparator.ComparisonMode;
import io.schemaregistry.abtest.comparator.ResponseComparator;
import io.schemaregistry.abtest.runner.HttpExecutor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(15)
public class Phase15ErrorCodes extends AbstractTestPhase {

    static final String AVRO_ERR = """
            {"schema":"{\\"type\\":\\"record\\",\\"name\\":\\"ErrTest\\",\\"namespace\\":\\"com.example\\",\\"fields\\":[{\\"name\\":\\"e1\\",\\"type\\":\\"string\\"}]}"}""";

    public Phase15ErrorCodes(HttpExecutor httpExecutor, ResponseComparator comparator) {
        super(httpExecutor, comparator);
    }

    @Override
    public String name() {
        return "Phase 15: Extended Error Codes";
    }

    @Override
    protected void runTests() {
        // Setup
        test("Setup: register err-test v1", "POST", "/subjects/err-test-value/versions", AVRO_ERR,
                ComparisonMode.EXACT);

        // --- 42204: Invalid mode ---
        test("Invalid mode (42204)", "PUT", "/mode",
                "{\"mode\":\"INVALID\"}", ComparisonMode.STATUS_AND_ERROR_CODE);

        // --- 42205: Operation not permitted in READONLY ---
        // Set READONLY mode
        test("Set READONLY mode", "PUT", "/mode",
                "{\"mode\":\"READONLY\"}", ComparisonMode.EXACT);

        // Attempt to register in READONLY mode (42205)
        test("Register in READONLY mode (42205)", "POST", "/subjects/err-test-value/versions", AVRO_ERR,
                ComparisonMode.STATUS_AND_ERROR_CODE);

        // Attempt to delete in READONLY mode (42205)
        test("Delete in READONLY mode (42205)", "DELETE", "/subjects/err-test-value/versions/1", null,
                ComparisonMode.STATUS_AND_ERROR_CODE);

        // Reset mode back to READWRITE
        test("Reset mode to READWRITE", "PUT", "/mode",
                "{\"mode\":\"READWRITE\"}", ComparisonMode.EXACT);

        // --- 40901: Incompatible schema registration rejection ---
        // Set strict compatibility
        test("Set FULL compatibility", "PUT", "/config/err-test-value",
                "{\"compatibility\":\"FULL\"}", ComparisonMode.EXACT);

        // Try to register incompatible schema (type change: string -> int)
        test("Incompatible registration rejected (40901)", "POST", "/subjects/err-test-value/versions",
                "{\"schema\":\"{\\\"type\\\":\\\"record\\\",\\\"name\\\":\\\"ErrTest\\\",\\\"namespace\\\":\\\"com.example\\\",\\\"fields\\\":[{\\\"name\\\":\\\"e1\\\",\\\"type\\\":\\\"int\\\"}]}\"}",
                ComparisonMode.STATUS_AND_ERROR_CODE);

        // Clean up config
        test("Delete subject config err-test-value", "DELETE", "/config/err-test-value", null,
                ComparisonMode.JSON_STRUCTURE, new String[]{"compatibilityLevel"});

        // --- 40408: Subject-level compatibility not configured ---
        test("Subject config not configured defaultToGlobal=false (40408)", "GET",
                "/config/err-test-value?defaultToGlobal=false", null,
                ComparisonMode.STATUS_AND_ERROR_CODE);

        // --- 40409: Subject-level mode not configured ---
        test("Subject mode not configured (40409)", "GET",
                "/mode/err-test-value?defaultToGlobal=false", null,
                ComparisonMode.STATUS_AND_ERROR_CODE);

        // Cleanup
        test("Cleanup: soft delete err-test-value", "DELETE", "/subjects/err-test-value", null,
                ComparisonMode.SET);
        test("Cleanup: permanent delete err-test-value", "DELETE", "/subjects/err-test-value?permanent=true", null,
                ComparisonMode.SET);
    }
}
