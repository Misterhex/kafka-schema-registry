package io.schemaregistry.abtest.tests;

import io.schemaregistry.abtest.comparator.ComparisonMode;
import io.schemaregistry.abtest.comparator.ResponseComparator;
import io.schemaregistry.abtest.runner.HttpExecutor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(12)
public class Phase12Metadata extends AbstractTestPhase {

    public Phase12Metadata(HttpExecutor httpExecutor, ResponseComparator comparator) {
        super(httpExecutor, comparator);
    }

    @Override
    public String name() {
        return "Phase 12: Metadata (Expected to Differ)";
    }

    @Override
    protected void runTests() {
        test("Cluster metadata ID", "GET", "/v1/metadata/id", null, ComparisonMode.STRUCTURE_ONLY);
        test("Server version", "GET", "/v1/metadata/version", null, ComparisonMode.STRUCTURE_ONLY);
    }
}
