package io.schemaregistry.abtest.tests;

import io.schemaregistry.abtest.comparator.ComparisonMode;
import io.schemaregistry.abtest.comparator.ResponseComparator;
import io.schemaregistry.abtest.runner.HttpExecutor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(4)
public class Phase4Compatibility extends AbstractTestPhase {

    static final String COMPAT_SCHEMA = """
            {"schema":"{\\"type\\":\\"record\\",\\"name\\":\\"Test\\",\\"namespace\\":\\"com.example\\",\\"fields\\":[{\\"name\\":\\"f1\\",\\"type\\":\\"string\\"},{\\"name\\":\\"f2\\",\\"type\\":[\\"null\\",\\"string\\"],\\"default\\":null},{\\"name\\":\\"f3\\",\\"type\\":[\\"null\\",\\"int\\"],\\"default\\":null}]}"}""";

    static final String INCOMPAT_SCHEMA = """
            {"schema":"{\\"type\\":\\"record\\",\\"name\\":\\"Test\\",\\"namespace\\":\\"com.example\\",\\"fields\\":[{\\"name\\":\\"f1\\",\\"type\\":\\"int\\"}]}"}""";

    public Phase4Compatibility(HttpExecutor httpExecutor, ResponseComparator comparator) {
        super(httpExecutor, comparator);
    }

    @Override
    public String name() {
        return "Phase 4: Compatibility Checks";
    }

    @Override
    protected void runTests() {
        test("Compatible schema vs latest", "POST",
                "/compatibility/subjects/ab-test-value/versions/latest", COMPAT_SCHEMA,
                ComparisonMode.JSON_STRUCTURE, new String[]{"is_compatible"});
        test("Incompatible schema vs latest", "POST",
                "/compatibility/subjects/ab-test-value/versions/latest", INCOMPAT_SCHEMA,
                ComparisonMode.JSON_STRUCTURE, new String[]{"is_compatible"});
        test("Compatible schema vs version 1", "POST",
                "/compatibility/subjects/ab-test-value/versions/1", COMPAT_SCHEMA,
                ComparisonMode.JSON_STRUCTURE, new String[]{"is_compatible"});
        test("Compatible schema vs all versions", "POST",
                "/compatibility/subjects/ab-test-value/versions", COMPAT_SCHEMA,
                ComparisonMode.JSON_STRUCTURE, new String[]{"is_compatible"});
    }
}
