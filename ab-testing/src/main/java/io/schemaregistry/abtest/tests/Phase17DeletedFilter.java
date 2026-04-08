package io.schemaregistry.abtest.tests;

import io.schemaregistry.abtest.comparator.ComparisonMode;
import io.schemaregistry.abtest.comparator.ResponseComparator;
import io.schemaregistry.abtest.runner.HttpExecutor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * The {@code ?deleted=true} query parameter on subjects/versions endpoints
 * is required for clients to inspect soft-deleted entries. Confirms the
 * mirror honors the parameter the same way Confluent SR does.
 */
@Component
@Order(17)
public class Phase17DeletedFilter extends AbstractTestPhase {

    static final String S = """
            {"schema":"{\\"type\\":\\"record\\",\\"name\\":\\"D17\\",\\"fields\\":[{\\"name\\":\\"x\\",\\"type\\":\\"int\\"}]}"}""";

    public Phase17DeletedFilter(HttpExecutor httpExecutor, ResponseComparator comparator) {
        super(httpExecutor, comparator);
    }

    @Override
    public String name() {
        return "Phase 17: ?deleted=true filter";
    }

    @Override
    protected void runTests() {
        test("Bootstrap subject", "POST", "/subjects/del17-ab-test/versions", S,
                ComparisonMode.JSON_STRUCTURE, new String[]{"id"});
        test("Soft delete version 1", "DELETE", "/subjects/del17-ab-test/versions/1", null,
                ComparisonMode.EXACT);
        test("Versions without deleted=true is empty (404)", "GET",
                "/subjects/del17-ab-test/versions", null,
                ComparisonMode.STATUS_AND_ERROR_CODE);
        test("Versions with deleted=true returns [1]", "GET",
                "/subjects/del17-ab-test/versions?deleted=true", null,
                ComparisonMode.EXACT);
        test("Subjects with deleted=true contains del17", "GET",
                "/subjects?deleted=true", null, ComparisonMode.SET);
        test("Get specific deleted version with ?deleted=true", "GET",
                "/subjects/del17-ab-test/versions/1?deleted=true", null,
                ComparisonMode.JSON_STRUCTURE, new String[]{"subject", "version", "id", "schemaType"});
        test("Hard delete version 1", "DELETE",
                "/subjects/del17-ab-test/versions/1?permanent=true", null, ComparisonMode.EXACT);
    }
}
