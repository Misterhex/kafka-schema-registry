package io.schemaregistry.abtest.runner;

import io.schemaregistry.abtest.model.TestReport;
import io.schemaregistry.abtest.model.TestResult;
import io.schemaregistry.abtest.tests.TestPhase;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class AbTestRunner {

    private final List<TestPhase> phases;

    public AbTestRunner(List<TestPhase> phases) {
        this.phases = phases;
    }

    public TestReport execute() {
        List<TestResult> allResults = new ArrayList<>();
        int nextNumber = 1;

        for (TestPhase phase : phases) {
            List<TestResult> phaseResults = phase.execute(nextNumber);
            allResults.addAll(phaseResults);
            nextNumber += phaseResults.size();
        }

        return TestReport.from(allResults);
    }
}
