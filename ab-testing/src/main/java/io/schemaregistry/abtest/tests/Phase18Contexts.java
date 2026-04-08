package io.schemaregistry.abtest.tests;

import io.schemaregistry.abtest.comparator.ComparisonMode;
import io.schemaregistry.abtest.comparator.ResponseComparator;
import io.schemaregistry.abtest.runner.HttpExecutor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Schema contexts — Confluent SR namespaces subjects under a context using
 * the {@code :.context:subject} notation. We assert at least the listing
 * endpoint and the default context behaviour matches Confluent.
 */
@Component
@Order(18)
public class Phase18Contexts extends AbstractTestPhase {

    public Phase18Contexts(HttpExecutor httpExecutor, ResponseComparator comparator) {
        super(httpExecutor, comparator);
    }

    @Override
    public String name() {
        return "Phase 18: Contexts";
    }

    @Override
    protected void runTests() {
        test("List contexts", "GET", "/contexts", null, ComparisonMode.SET);
        test("List subjects in default context", "GET", "/contexts/./subjects", null,
                ComparisonMode.SET);
    }
}
