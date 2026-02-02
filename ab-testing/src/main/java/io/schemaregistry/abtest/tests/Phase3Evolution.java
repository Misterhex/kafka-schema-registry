package io.schemaregistry.abtest.tests;

import io.schemaregistry.abtest.comparator.ComparisonMode;
import io.schemaregistry.abtest.comparator.ResponseComparator;
import io.schemaregistry.abtest.runner.HttpExecutor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(3)
public class Phase3Evolution extends AbstractTestPhase {

    static final String AVRO_V2 = """
            {"schema":"{\\"type\\":\\"record\\",\\"name\\":\\"Test\\",\\"namespace\\":\\"com.example\\",\\"fields\\":[{\\"name\\":\\"f1\\",\\"type\\":\\"string\\"},{\\"name\\":\\"f2\\",\\"type\\":[\\"null\\",\\"string\\"],\\"default\\":null}]}"}""";

    public Phase3Evolution(HttpExecutor httpExecutor, ResponseComparator comparator) {
        super(httpExecutor, comparator);
    }

    @Override
    public String name() {
        return "Phase 3: Schema Evolution";
    }

    @Override
    protected void runTests() {
        test("Register v2 (backward compatible)", "POST", "/subjects/ab-test-value/versions", AVRO_V2, ComparisonMode.EXACT);
        test("Versions list [1,2]", "GET", "/subjects/ab-test-value/versions", null, ComparisonMode.EXACT);
        test("Latest is version 2", "GET", "/subjects/ab-test-value/versions/latest", null,
                ComparisonMode.JSON_STRUCTURE, new String[]{"subject", "version", "id"});
        test("Lookup by content (v1)", "POST", "/subjects/ab-test-value", Phase2Registration.AVRO_V1,
                ComparisonMode.JSON_STRUCTURE, new String[]{"subject", "version", "id"});
    }
}
