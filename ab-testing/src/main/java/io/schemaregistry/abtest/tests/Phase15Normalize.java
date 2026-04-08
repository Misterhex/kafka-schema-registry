package io.schemaregistry.abtest.tests;

import io.schemaregistry.abtest.comparator.ComparisonMode;
import io.schemaregistry.abtest.comparator.ResponseComparator;
import io.schemaregistry.abtest.runner.HttpExecutor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * The {@code ?normalize=true} query parameter on registration / lookup
 * causes Confluent SR to canonicalize the schema string before hashing,
 * meaning two semantically-identical schemas with different whitespace,
 * field order, or alias ordering deduplicate to the same id. Verifies the
 * mirror behaves the same way.
 */
@Component
@Order(15)
public class Phase15Normalize extends AbstractTestPhase {

    // Same record, different whitespace and field order.
    static final String SCHEMA_A = """
            {"schema":"{\\"type\\":\\"record\\",\\"name\\":\\"NormTest\\",\\"namespace\\":\\"com.example\\",\\"fields\\":[{\\"name\\":\\"a\\",\\"type\\":\\"string\\"},{\\"name\\":\\"b\\",\\"type\\":\\"int\\"}]}"}""";

    static final String SCHEMA_B_REORDERED = """
            {"schema":"{ \\"type\\" : \\"record\\" , \\"name\\" : \\"NormTest\\" , \\"namespace\\" : \\"com.example\\" , \\"fields\\" : [ { \\"name\\" : \\"a\\" , \\"type\\" : \\"string\\" } , { \\"name\\" : \\"b\\" , \\"type\\" : \\"int\\" } ] }"}""";

    public Phase15Normalize(HttpExecutor httpExecutor, ResponseComparator comparator) {
        super(httpExecutor, comparator);
    }

    @Override
    public String name() {
        return "Phase 15: Normalize Parameter";
    }

    @Override
    protected void runTests() {
        test("Register normalized v1", "POST", "/subjects/normalize-ab-test/versions?normalize=true",
                SCHEMA_A, ComparisonMode.JSON_STRUCTURE, new String[]{"id"});
        test("Register equivalent (different whitespace) - normalized dedup",
                "POST", "/subjects/normalize-ab-test/versions?normalize=true",
                SCHEMA_B_REORDERED, ComparisonMode.JSON_STRUCTURE, new String[]{"id"});
        test("Lookup with normalize=true returns canonical id",
                "POST", "/subjects/normalize-ab-test?normalize=true",
                SCHEMA_B_REORDERED, ComparisonMode.JSON_STRUCTURE, new String[]{"id"});
        test("Versions list still has only one version",
                "GET", "/subjects/normalize-ab-test/versions", null, ComparisonMode.EXACT);
    }
}
