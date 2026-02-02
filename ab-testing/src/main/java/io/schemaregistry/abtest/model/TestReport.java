package io.schemaregistry.abtest.model;

import java.time.Instant;
import java.util.List;

public record TestReport(
        String timestamp,
        Summary summary,
        List<TestResult> results
) {
    public static TestReport from(List<TestResult> results) {
        int match = 0, diff = 0, structural = 0;
        for (TestResult r : results) {
            switch (r.status()) {
                case MATCH -> match++;
                case DIFF -> diff++;
                case STRUCTURAL -> structural++;
            }
        }
        return new TestReport(
                Instant.now().toString(),
                new Summary(results.size(), match, diff, structural),
                results
        );
    }

    public record Summary(int total, int match, int diff, int structural) {
        public double compatibilityPercent() {
            if (total == 0) return 100.0;
            return (match + structural) * 100.0 / total;
        }
    }
}
