package io.schemaregistry.abtest.tests;

import io.schemaregistry.abtest.comparator.ComparisonMode;
import io.schemaregistry.abtest.comparator.ResponseComparator;
import io.schemaregistry.abtest.runner.HttpExecutor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(7)
public class Phase7ModeOperations extends AbstractTestPhase {

    public Phase7ModeOperations(HttpExecutor httpExecutor, ResponseComparator comparator) {
        super(httpExecutor, comparator);
    }

    @Override
    public String name() {
        return "Phase 7: Mode Operations";
    }

    @Override
    protected void runTests() {
        test("Default mode READWRITE", "GET", "/mode", null, ComparisonMode.EXACT);
        test("Set global mode READONLY", "PUT", "/mode",
                "{\"mode\":\"READONLY\"}", ComparisonMode.EXACT);
        test("Verify READONLY", "GET", "/mode", null, ComparisonMode.EXACT);
        test("Reset global mode", "DELETE", "/mode", null, ComparisonMode.EXACT);
        test("Set subject mode IMPORT", "PUT", "/mode/ab-test-value",
                "{\"mode\":\"IMPORT\"}", ComparisonMode.EXACT);
        test("Get subject mode", "GET", "/mode/ab-test-value", null, ComparisonMode.EXACT);
        test("Delete subject mode", "DELETE", "/mode/ab-test-value", null, ComparisonMode.EXACT);
    }
}
