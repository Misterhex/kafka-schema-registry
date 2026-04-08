package io.schemaregistry.abtest.tests;

import io.schemaregistry.abtest.comparator.ComparisonMode;
import io.schemaregistry.abtest.comparator.ResponseComparator;
import io.schemaregistry.abtest.runner.HttpExecutor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Coverage for Protobuf schemas. The earlier phases only exercise Avro and
 * JSON; this phase confirms that PROTOBUF registrations, lookups, and
 * compatibility checks behave identically to Confluent SR.
 */
@Component
@Order(13)
public class Phase13Protobuf extends AbstractTestPhase {

    static final String PROTOBUF_V1 = """
            {"schema":"syntax = \\"proto3\\";\\npackage com.example;\\nmessage User {\\n  string name = 1;\\n  int32 age = 2;\\n}\\n","schemaType":"PROTOBUF"}""";

    static final String PROTOBUF_V2 = """
            {"schema":"syntax = \\"proto3\\";\\npackage com.example;\\nmessage User {\\n  string name = 1;\\n  int32 age = 2;\\n  string email = 3;\\n}\\n","schemaType":"PROTOBUF"}""";

    public Phase13Protobuf(HttpExecutor httpExecutor, ResponseComparator comparator) {
        super(httpExecutor, comparator);
    }

    @Override
    public String name() {
        return "Phase 13: Protobuf Schema Support";
    }

    @Override
    protected void runTests() {
        test("Register Protobuf v1", "POST", "/subjects/proto-ab-test/versions", PROTOBUF_V1,
                ComparisonMode.JSON_STRUCTURE, new String[]{"id"});
        test("Verify schemaType=PROTOBUF", "GET", "/subjects/proto-ab-test/versions/1", null,
                ComparisonMode.JSON_STRUCTURE, new String[]{"subject", "version", "schemaType"});
        test("Lookup Protobuf by content", "POST", "/subjects/proto-ab-test", PROTOBUF_V1,
                ComparisonMode.JSON_STRUCTURE, new String[]{"id", "schemaType"});
        test("Compatible Protobuf evolution (add optional field)", "POST",
                "/compatibility/subjects/proto-ab-test/versions/latest", PROTOBUF_V2,
                ComparisonMode.JSON_STRUCTURE, new String[]{"is_compatible"});
        test("Register Protobuf v2", "POST", "/subjects/proto-ab-test/versions", PROTOBUF_V2,
                ComparisonMode.JSON_STRUCTURE, new String[]{"id"});
        test("List Protobuf versions", "GET", "/subjects/proto-ab-test/versions", null,
                ComparisonMode.EXACT);
    }
}
