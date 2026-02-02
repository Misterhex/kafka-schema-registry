package io.schemaregistry.abtest.tests;

import io.schemaregistry.abtest.comparator.ComparisonMode;
import io.schemaregistry.abtest.comparator.ResponseComparator;
import io.schemaregistry.abtest.runner.HttpExecutor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(2)
public class Phase2Registration extends AbstractTestPhase {

    static final String AVRO_V1 = """
            {"schema":"{\\"type\\":\\"record\\",\\"name\\":\\"Test\\",\\"namespace\\":\\"com.example\\",\\"fields\\":[{\\"name\\":\\"f1\\",\\"type\\":\\"string\\"}]}"}""";

    public Phase2Registration(HttpExecutor httpExecutor, ResponseComparator comparator) {
        super(httpExecutor, comparator);
    }

    @Override
    public String name() {
        return "Phase 2: Schema Registration - Avro";
    }

    @Override
    protected void runTests() {
        test("Register Avro v1", "POST", "/subjects/ab-test-value/versions", AVRO_V1, ComparisonMode.EXACT);
        test("Subjects list contains ab-test-value", "GET", "/subjects", null, ComparisonMode.SET);
        test("List versions", "GET", "/subjects/ab-test-value/versions", null, ComparisonMode.EXACT);
        test("Get version 1", "GET", "/subjects/ab-test-value/versions/1", null,
                ComparisonMode.JSON_STRUCTURE, new String[]{"subject", "version", "id", "schemaType"});
        test("Get latest version", "GET", "/subjects/ab-test-value/versions/latest", null,
                ComparisonMode.JSON_STRUCTURE, new String[]{"subject", "version", "id", "schemaType"});
        test("Get schema by global ID", "GET", "/schemas/ids/1", null,
                ComparisonMode.JSON_STRUCTURE, new String[]{"schemaType"});
        test("Get raw schema by ID", "GET", "/schemas/ids/1/schema", null, ComparisonMode.EXACT);
        test("Get raw schema by version", "GET", "/subjects/ab-test-value/versions/1/schema", null, ComparisonMode.EXACT);
        test("Re-register same schema (dedup)", "POST", "/subjects/ab-test-value/versions", AVRO_V1, ComparisonMode.EXACT);
    }
}
