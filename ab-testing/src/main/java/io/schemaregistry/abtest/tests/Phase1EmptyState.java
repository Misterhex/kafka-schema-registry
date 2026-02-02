package io.schemaregistry.abtest.tests;

import io.schemaregistry.abtest.comparator.ComparisonMode;
import io.schemaregistry.abtest.comparator.ResponseComparator;
import io.schemaregistry.abtest.runner.HttpExecutor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(1)
public class Phase1EmptyState extends AbstractTestPhase {

    public Phase1EmptyState(HttpExecutor httpExecutor, ResponseComparator comparator) {
        super(httpExecutor, comparator);
    }

    @Override
    public String name() {
        return "Phase 1: Empty State";
    }

    @Override
    protected void runTests() {
        test("Root endpoint", "GET", "/", null, ComparisonMode.EXACT);
        test("Empty subjects list", "GET", "/subjects", null, ComparisonMode.EXACT);
        test("Default config (BACKWARD)", "GET", "/config", null, ComparisonMode.EXACT);
        test("Default mode (READWRITE)", "GET", "/mode", null, ComparisonMode.EXACT);
        test("Schema types", "GET", "/schemas/types", null, ComparisonMode.SET);
        test("Contexts", "GET", "/contexts", null, ComparisonMode.EXACT);
    }
}
