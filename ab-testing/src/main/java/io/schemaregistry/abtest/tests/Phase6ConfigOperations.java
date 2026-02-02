package io.schemaregistry.abtest.tests;

import io.schemaregistry.abtest.comparator.ComparisonMode;
import io.schemaregistry.abtest.comparator.ResponseComparator;
import io.schemaregistry.abtest.runner.HttpExecutor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(6)
public class Phase6ConfigOperations extends AbstractTestPhase {

    public Phase6ConfigOperations(HttpExecutor httpExecutor, ResponseComparator comparator) {
        super(httpExecutor, comparator);
    }

    @Override
    public String name() {
        return "Phase 6: Config Operations";
    }

    @Override
    protected void runTests() {
        test("Set subject config FULL", "PUT", "/config/ab-test-value",
                "{\"compatibility\":\"FULL\"}", ComparisonMode.EXACT);
        test("Get subject config", "GET", "/config/ab-test-value", null, ComparisonMode.EXACT);
        test("Set global config NONE", "PUT", "/config",
                "{\"compatibility\":\"NONE\"}", ComparisonMode.EXACT);
        test("Get global config", "GET", "/config", null, ComparisonMode.EXACT);
        test("Delete subject config", "DELETE", "/config/ab-test-value", null,
                ComparisonMode.JSON_STRUCTURE, new String[]{"compatibilityLevel"});
        test("Delete global config", "DELETE", "/config", null,
                ComparisonMode.JSON_STRUCTURE, new String[]{"compatibilityLevel"});
        test("Verify reset to BACKWARD", "GET", "/config", null, ComparisonMode.EXACT);
    }
}
