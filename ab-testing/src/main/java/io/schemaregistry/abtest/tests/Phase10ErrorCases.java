package io.schemaregistry.abtest.tests;

import io.schemaregistry.abtest.comparator.ComparisonMode;
import io.schemaregistry.abtest.comparator.ResponseComparator;
import io.schemaregistry.abtest.runner.HttpExecutor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(10)
public class Phase10ErrorCases extends AbstractTestPhase {

    public Phase10ErrorCases(HttpExecutor httpExecutor, ResponseComparator comparator) {
        super(httpExecutor, comparator);
    }

    @Override
    public String name() {
        return "Phase 10: Error Cases";
    }

    @Override
    protected void runTests() {
        test("Subject not found (40401)", "GET", "/subjects/nonexistent/versions", null,
                ComparisonMode.STATUS_AND_ERROR_CODE);
        test("Schema ID not found (40403)", "GET", "/schemas/ids/99999", null,
                ComparisonMode.STATUS_AND_ERROR_CODE);
        test("Version not found (40402)", "GET", "/subjects/ab-test-value/versions/999", null,
                ComparisonMode.STATUS_AND_ERROR_CODE);
        test("Invalid schema (42201)", "POST", "/subjects/ab-test-value/versions",
                "{\"schema\":\"{\\\"type\\\":\\\"invalid\\\"}\"}",
                ComparisonMode.STATUS_AND_ERROR_CODE);
        test("Invalid compatibility level (42203)", "PUT", "/config",
                "{\"compatibility\":\"INVALID\"}",
                ComparisonMode.STATUS_AND_ERROR_CODE);
        test("Invalid version format (42202)", "GET", "/subjects/ab-test-value/versions/abc", null,
                ComparisonMode.STATUS_AND_ERROR_CODE);
    }
}
