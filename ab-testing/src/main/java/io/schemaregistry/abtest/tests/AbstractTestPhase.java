package io.schemaregistry.abtest.tests;

import io.schemaregistry.abtest.comparator.ComparisonMode;
import io.schemaregistry.abtest.comparator.ResponseComparator;
import io.schemaregistry.abtest.comparator.ResponseComparator.ComparisonResult;
import io.schemaregistry.abtest.model.TestResult;
import io.schemaregistry.abtest.runner.HttpExecutor;
import io.schemaregistry.abtest.runner.HttpExecutor.DualResponse;

import java.util.ArrayList;
import java.util.List;

public abstract class AbstractTestPhase implements TestPhase {

    protected final HttpExecutor httpExecutor;
    protected final ResponseComparator comparator;
    protected final List<TestResult> results = new ArrayList<>();
    protected int currentNumber;

    protected AbstractTestPhase(HttpExecutor httpExecutor, ResponseComparator comparator) {
        this.httpExecutor = httpExecutor;
        this.comparator = comparator;
    }

    protected TestResult test(String testName, String method, String endpoint, String body, ComparisonMode mode) {
        return test(testName, method, endpoint, body, mode, null);
    }

    protected TestResult test(String testName, String method, String endpoint, String body,
                              ComparisonMode mode, String[] fields) {
        DualResponse dual = httpExecutor.execute(method, endpoint, body);
        ComparisonResult comparison = comparator.compare(dual.confluent(), dual.mirror(), mode, fields);

        TestResult result = new TestResult(
                currentNumber++,
                name(),
                testName,
                method,
                endpoint,
                body,
                dual.confluent().status(),
                dual.confluent().body(),
                dual.confluent().contentType(),
                dual.mirror().status(),
                dual.mirror().body(),
                dual.mirror().contentType(),
                mode,
                comparison.status(),
                comparison.detail()
        );
        results.add(result);
        return result;
    }

    @Override
    public List<TestResult> execute(int startNumber) {
        this.currentNumber = startNumber;
        this.results.clear();
        runTests();
        return results;
    }

    protected abstract void runTests();
}
