package io.schemaregistry.abtest.tests;

import io.schemaregistry.abtest.comparator.ComparisonMode;
import io.schemaregistry.abtest.comparator.ResponseComparator;
import io.schemaregistry.abtest.runner.HttpExecutor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(16)
public class Phase16ProtobufAndAdvanced extends AbstractTestPhase {

    static final String PROTOBUF_SCHEMA = """
            {"schema":"syntax = \\"proto3\\"; message ProtoTest { string name = 1; }","schemaType":"PROTOBUF"}""";

    static final String AVRO_DEDUP = """
            {"schema":"{\\"type\\":\\"record\\",\\"name\\":\\"DedupTest\\",\\"namespace\\":\\"com.example\\",\\"fields\\":[{\\"name\\":\\"d1\\",\\"type\\":\\"string\\"}]}"}""";

    public Phase16ProtobufAndAdvanced(HttpExecutor httpExecutor, ResponseComparator comparator) {
        super(httpExecutor, comparator);
    }

    @Override
    public String name() {
        return "Phase 16: Protobuf & Advanced Scenarios";
    }

    @Override
    protected void runTests() {
        // --- Protobuf schema registration ---
        test("Register Protobuf schema", "POST", "/subjects/proto-test-value/versions", PROTOBUF_SCHEMA,
                ComparisonMode.JSON_STRUCTURE, new String[]{"id"});

        test("Verify Protobuf schemaType", "GET", "/subjects/proto-test-value/versions/1", null,
                ComparisonMode.JSON_STRUCTURE, new String[]{"subject", "version", "schemaType"});

        test("Get Protobuf raw schema by version", "GET", "/subjects/proto-test-value/versions/1/schema", null,
                ComparisonMode.EXACT);

        test("Get Protobuf schema by ID", "GET", "/subjects/proto-test-value/versions/latest", null,
                ComparisonMode.JSON_STRUCTURE, new String[]{"subject", "version", "id", "schemaType"});

        // --- Special version identifiers ---
        // -1 is an alias for latest (used by some clients)
        test("Get version -1 (latest alias)", "GET", "/subjects/ab-test-value/versions/-1", null,
                ComparisonMode.JSON_STRUCTURE, new String[]{"subject", "version", "id", "schemaType"});

        // -2 is an alias for earliest
        test("Get version -2 (earliest alias)", "GET", "/subjects/ab-test-value/versions/-2", null,
                ComparisonMode.JSON_STRUCTURE, new String[]{"subject", "version", "id", "schemaType"});

        // --- Cross-subject schema ID dedup ---
        // Register the same schema under a different subject - should get the same schema ID
        test("Register dedup schema under subject A", "POST", "/subjects/dedup-test-a/versions", AVRO_DEDUP,
                ComparisonMode.EXACT);

        test("Register same schema under subject B", "POST", "/subjects/dedup-test-b/versions", AVRO_DEDUP,
                ComparisonMode.EXACT);

        // Both registrations should return the same ID (dedup) - EXACT match verifies this
        // Verify schema metadata for both subjects
        test("Verify dedup: subject A version 1", "GET", "/subjects/dedup-test-a/versions/1", null,
                ComparisonMode.JSON_STRUCTURE, new String[]{"subject", "version", "id"});

        test("Verify dedup: subject B version 1", "GET", "/subjects/dedup-test-b/versions/1", null,
                ComparisonMode.JSON_STRUCTURE, new String[]{"subject", "version", "id"});

        // --- Subject prefix filtering ---
        test("Subjects with prefix=dedup", "GET", "/subjects?subjectPrefix=dedup", null, ComparisonMode.SET);
        test("Subjects with prefix=proto", "GET", "/subjects?subjectPrefix=proto", null, ComparisonMode.SET);

        // Cleanup
        test("Cleanup: soft delete proto-test-value", "DELETE", "/subjects/proto-test-value", null, ComparisonMode.SET);
        test("Cleanup: permanent delete proto-test-value", "DELETE", "/subjects/proto-test-value?permanent=true", null,
                ComparisonMode.SET);
        test("Cleanup: soft delete dedup-test-a", "DELETE", "/subjects/dedup-test-a", null, ComparisonMode.SET);
        test("Cleanup: permanent delete dedup-test-a", "DELETE", "/subjects/dedup-test-a?permanent=true", null,
                ComparisonMode.SET);
        test("Cleanup: soft delete dedup-test-b", "DELETE", "/subjects/dedup-test-b", null, ComparisonMode.SET);
        test("Cleanup: permanent delete dedup-test-b", "DELETE", "/subjects/dedup-test-b?permanent=true", null,
                ComparisonMode.SET);
    }
}
