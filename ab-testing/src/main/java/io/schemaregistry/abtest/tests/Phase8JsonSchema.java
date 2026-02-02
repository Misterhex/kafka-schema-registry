package io.schemaregistry.abtest.tests;

import io.schemaregistry.abtest.comparator.ComparisonMode;
import io.schemaregistry.abtest.comparator.ResponseComparator;
import io.schemaregistry.abtest.runner.HttpExecutor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(8)
public class Phase8JsonSchema extends AbstractTestPhase {

    static final String JSON_SCHEMA = """
            {"schema":"{\\"type\\":\\"object\\",\\"properties\\":{\\"name\\":{\\"type\\":\\"string\\"}}}","schemaType":"JSON"}""";

    public Phase8JsonSchema(HttpExecutor httpExecutor, ResponseComparator comparator) {
        super(httpExecutor, comparator);
    }

    @Override
    public String name() {
        return "Phase 8: JSON Schema Support";
    }

    @Override
    protected void runTests() {
        test("Register JSON schema", "POST", "/subjects/json-ab-test/versions", JSON_SCHEMA,
                ComparisonMode.JSON_STRUCTURE, new String[]{"id"});
        test("Verify schemaType=JSON", "GET", "/subjects/json-ab-test/versions/1", null,
                ComparisonMode.JSON_STRUCTURE, new String[]{"subject", "version", "schemaType"});
    }
}
