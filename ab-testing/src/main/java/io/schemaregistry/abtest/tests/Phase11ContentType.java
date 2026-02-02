package io.schemaregistry.abtest.tests;

import io.schemaregistry.abtest.comparator.ComparisonMode;
import io.schemaregistry.abtest.comparator.ResponseComparator;
import io.schemaregistry.abtest.comparator.ResponseComparator.ComparisonResult;
import io.schemaregistry.abtest.model.TestResult;
import io.schemaregistry.abtest.model.TestResult.MatchStatus;
import io.schemaregistry.abtest.runner.HttpExecutor;
import io.schemaregistry.abtest.runner.HttpExecutor.DualResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Order(11)
public class Phase11ContentType extends AbstractTestPhase {

    public Phase11ContentType(HttpExecutor httpExecutor, ResponseComparator comparator) {
        super(httpExecutor, comparator);
    }

    @Override
    public String name() {
        return "Phase 11: Content-Type Verification";
    }

    @Override
    protected void runTests() {
        testContentType("Success response content-type", "/config");
        testContentType("Error response content-type", "/schemas/ids/99999");
    }

    private void testContentType(String testName, String endpoint) {
        DualResponse dual = httpExecutor.execute("GET", endpoint, null);

        boolean confluentOk = dual.confluent().contentType().contains("application/vnd.schemaregistry.v1+json");
        boolean mirrorOk = dual.mirror().contentType().contains("application/vnd.schemaregistry.v1+json");

        MatchStatus status;
        String detail = null;
        if (confluentOk && mirrorOk) {
            status = MatchStatus.MATCH;
        } else {
            status = MatchStatus.DIFF;
            detail = "Content-Type: confluent=%s mirror=%s".formatted(
                    dual.confluent().contentType(), dual.mirror().contentType());
        }

        results.add(new TestResult(
                currentNumber++,
                name(),
                testName,
                "GET",
                endpoint,
                null,
                dual.confluent().status(),
                dual.confluent().body(),
                dual.confluent().contentType(),
                dual.mirror().status(),
                dual.mirror().body(),
                dual.mirror().contentType(),
                ComparisonMode.EXACT,
                status,
                detail
        ));
    }
}
