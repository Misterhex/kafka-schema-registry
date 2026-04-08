package io.schemaregistry.abtest.tests;

import io.schemaregistry.abtest.comparator.ComparisonMode;
import io.schemaregistry.abtest.comparator.ResponseComparator;
import io.schemaregistry.abtest.runner.HttpExecutor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Cross-subject schema references — a Confluent SR feature where one schema
 * imports another by name+version. Verifies that:
 * <ul>
 *   <li>Registering a schema with a {@code references} array succeeds.</li>
 *   <li>{@code referencedby} on the parent returns the dependent.</li>
 *   <li>Hard-deleting the parent while a reference exists is rejected.</li>
 * </ul>
 */
@Component
@Order(14)
public class Phase14SchemaReferences extends AbstractTestPhase {

    static final String PARENT_SCHEMA = """
            {"schema":"{\\"type\\":\\"record\\",\\"name\\":\\"Address\\",\\"namespace\\":\\"com.example\\",\\"fields\\":[{\\"name\\":\\"street\\",\\"type\\":\\"string\\"},{\\"name\\":\\"city\\",\\"type\\":\\"string\\"}]}"}""";

    static final String CHILD_SCHEMA_WITH_REF = """
            {"schema":"{\\"type\\":\\"record\\",\\"name\\":\\"Customer\\",\\"namespace\\":\\"com.example\\",\\"fields\\":[{\\"name\\":\\"name\\",\\"type\\":\\"string\\"},{\\"name\\":\\"address\\",\\"type\\":\\"com.example.Address\\"}]}","references":[{"name":"com.example.Address","subject":"address-ab-test","version":1}]}""";

    public Phase14SchemaReferences(HttpExecutor httpExecutor, ResponseComparator comparator) {
        super(httpExecutor, comparator);
    }

    @Override
    public String name() {
        return "Phase 14: Schema References";
    }

    @Override
    protected void runTests() {
        test("Register parent (Address)", "POST", "/subjects/address-ab-test/versions",
                PARENT_SCHEMA, ComparisonMode.JSON_STRUCTURE, new String[]{"id"});
        test("Register child with reference (Customer)", "POST",
                "/subjects/customer-ab-test/versions", CHILD_SCHEMA_WITH_REF,
                ComparisonMode.JSON_STRUCTURE, new String[]{"id"});
        test("Get child schema (references field present)", "GET",
                "/subjects/customer-ab-test/versions/1", null,
                ComparisonMode.JSON_STRUCTURE,
                new String[]{"subject", "version", "id", "schemaType", "references"});
        test("referencedby returns child id", "GET",
                "/subjects/address-ab-test/versions/1/referencedby", null, ComparisonMode.SET);
        test("Hard delete parent while referenced (must fail 422)", "DELETE",
                "/subjects/address-ab-test/versions/1?permanent=true", null,
                ComparisonMode.STATUS_AND_ERROR_CODE);
    }
}
