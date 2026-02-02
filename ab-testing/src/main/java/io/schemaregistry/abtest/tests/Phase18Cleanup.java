package io.schemaregistry.abtest.tests;

import io.schemaregistry.abtest.comparator.ComparisonMode;
import io.schemaregistry.abtest.comparator.ResponseComparator;
import io.schemaregistry.abtest.runner.HttpExecutor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(18)
public class Phase18Cleanup extends AbstractTestPhase {

    public Phase18Cleanup(HttpExecutor httpExecutor, ResponseComparator comparator) {
        super(httpExecutor, comparator);
    }

    @Override
    public String name() {
        return "Phase 18: Final Cleanup";
    }

    @Override
    protected void runTests() {
        // Clean up json-ab-test (soft-deleted in Phase 9, never permanently deleted)
        test("Permanent delete json-ab-test", "DELETE", "/subjects/json-ab-test?permanent=true", null,
                ComparisonMode.SET);

        // Clean up ab-test-value (has version 2 remaining after Phase 9 hard-deleted version 1)
        test("Soft delete ab-test-value", "DELETE", "/subjects/ab-test-value", null, ComparisonMode.SET);
        test("Permanent delete ab-test-value", "DELETE", "/subjects/ab-test-value?permanent=true", null,
                ComparisonMode.SET);

        // Reset global config to BACKWARD default
        test("Reset global config", "DELETE", "/config", null,
                ComparisonMode.JSON_STRUCTURE, new String[]{"compatibilityLevel"});

        // Verify clean state
        test("Verify subjects list empty", "GET", "/subjects", null, ComparisonMode.EXACT);
        test("Verify config reset to BACKWARD", "GET", "/config", null, ComparisonMode.EXACT);
        test("Verify mode reset to READWRITE", "GET", "/mode", null, ComparisonMode.EXACT);
    }
}
