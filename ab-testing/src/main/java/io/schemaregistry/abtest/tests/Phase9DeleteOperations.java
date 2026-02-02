package io.schemaregistry.abtest.tests;

import io.schemaregistry.abtest.comparator.ComparisonMode;
import io.schemaregistry.abtest.comparator.ResponseComparator;
import io.schemaregistry.abtest.runner.HttpExecutor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(9)
public class Phase9DeleteOperations extends AbstractTestPhase {

    public Phase9DeleteOperations(HttpExecutor httpExecutor, ResponseComparator comparator) {
        super(httpExecutor, comparator);
    }

    @Override
    public String name() {
        return "Phase 9: Delete Operations";
    }

    @Override
    protected void runTests() {
        test("Soft delete version 1", "DELETE", "/subjects/ab-test-value/versions/1", null, ComparisonMode.EXACT);
        test("Versions after soft delete", "GET", "/subjects/ab-test-value/versions", null, ComparisonMode.EXACT);
        test("Hard delete version 1", "DELETE", "/subjects/ab-test-value/versions/1?permanent=true", null, ComparisonMode.EXACT);
        test("Soft delete json-ab-test subject", "DELETE", "/subjects/json-ab-test", null, ComparisonMode.SET);
        test("Subjects after delete", "GET", "/subjects", null, ComparisonMode.SET);
    }
}
