package io.schemaregistry.abtest.tests;

import io.schemaregistry.abtest.comparator.ComparisonMode;
import io.schemaregistry.abtest.comparator.ResponseComparator;
import io.schemaregistry.abtest.runner.HttpExecutor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(5)
public class Phase5SchemaIdQueries extends AbstractTestPhase {

    public Phase5SchemaIdQueries(HttpExecutor httpExecutor, ResponseComparator comparator) {
        super(httpExecutor, comparator);
    }

    @Override
    public String name() {
        return "Phase 5: Schema ID Queries";
    }

    @Override
    protected void runTests() {
        test("Subjects using schema 1", "GET", "/schemas/ids/1/subjects", null, ComparisonMode.SET);
        test("Subjects using schema 2", "GET", "/schemas/ids/2/subjects", null, ComparisonMode.SET);
        test("Subject-version pairs for ID 1", "GET", "/schemas/ids/1/versions", null, ComparisonMode.SET);
        test("Subject-version pairs for ID 2", "GET", "/schemas/ids/2/versions", null, ComparisonMode.SET);
        test("Referenced by (empty)", "GET", "/subjects/ab-test-value/versions/1/referencedby", null, ComparisonMode.EXACT);
    }
}
