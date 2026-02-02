package io.schemaregistry.abtest.tests;

import io.schemaregistry.abtest.comparator.ComparisonMode;
import io.schemaregistry.abtest.comparator.ResponseComparator;
import io.schemaregistry.abtest.runner.HttpExecutor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(17)
public class Phase17ReferencesAndEdgeCases extends AbstractTestPhase {

    static final String ADDRESS_SCHEMA = """
            {"schema":"{\\"type\\":\\"record\\",\\"name\\":\\"Address\\",\\"namespace\\":\\"com.example\\",\\"fields\\":[{\\"name\\":\\"street\\",\\"type\\":\\"string\\"}]}"}""";

    static final String PERSON_SCHEMA_WITH_REF = """
            {"schema":"{\\"type\\":\\"record\\",\\"name\\":\\"Person\\",\\"namespace\\":\\"com.example\\",\\"fields\\":[{\\"name\\":\\"name\\",\\"type\\":\\"string\\"},{\\"name\\":\\"address\\",\\"type\\":\\"Address\\"}]}","references":[{"name":"com.example.Address","subject":"ref-address-value","version":1}]}""";

    public Phase17ReferencesAndEdgeCases(HttpExecutor httpExecutor, ResponseComparator comparator) {
        super(httpExecutor, comparator);
    }

    @Override
    public String name() {
        return "Phase 17: References & Edge Cases";
    }

    @Override
    protected void runTests() {
        // --- Schema references ---

        // Register the base schema (Address)
        test("Register Address schema (base)", "POST", "/subjects/ref-address-value/versions", ADDRESS_SCHEMA,
                ComparisonMode.EXACT);

        // Register Person schema with a reference to Address
        test("Register Person schema with reference", "POST", "/subjects/ref-person-value/versions",
                PERSON_SCHEMA_WITH_REF,
                ComparisonMode.JSON_STRUCTURE, new String[]{"id"});

        // Verify Person schema metadata includes references
        test("Get Person schema with references", "GET", "/subjects/ref-person-value/versions/1", null,
                ComparisonMode.JSON_STRUCTURE, new String[]{"subject", "version", "id", "schemaType"});

        // Query referencedby on Address - should return Person's schema ID
        test("Address referencedby returns Person ID", "GET",
                "/subjects/ref-address-value/versions/1/referencedby", null, ComparisonMode.SET);

        // Try to soft-delete Address (should fail with 42206 - reference exists)
        test("Delete referenced schema fails (42206)", "DELETE",
                "/subjects/ref-address-value/versions/1", null,
                ComparisonMode.STATUS_AND_ERROR_CODE);

        // Delete Person first (remove the reference)
        test("Soft delete Person schema", "DELETE", "/subjects/ref-person-value/versions/1", null,
                ComparisonMode.EXACT);

        // Now Address referencedby should be empty
        test("Address referencedby empty after Person deleted", "GET",
                "/subjects/ref-address-value/versions/1/referencedby", null, ComparisonMode.EXACT);

        // Now we can delete Address
        test("Soft delete Address schema (now allowed)", "DELETE",
                "/subjects/ref-address-value/versions/1", null, ComparisonMode.EXACT);

        // Cleanup: both subjects have all versions soft-deleted via version-level deletes.
        // Subject-level soft delete may return 40404 if auto-marked, or succeed if not.
        // Use STATUS_AND_ERROR_CODE to accept either case as long as both systems agree.
        test("Cleanup: soft delete ref-person-value subject", "DELETE", "/subjects/ref-person-value", null,
                ComparisonMode.STATUS_AND_ERROR_CODE);
        test("Cleanup: permanent delete ref-person-value", "DELETE",
                "/subjects/ref-person-value?permanent=true", null, ComparisonMode.SET);
        test("Cleanup: soft delete ref-address-value subject", "DELETE", "/subjects/ref-address-value", null,
                ComparisonMode.STATUS_AND_ERROR_CODE);
        test("Cleanup: permanent delete ref-address-value", "DELETE",
                "/subjects/ref-address-value?permanent=true", null, ComparisonMode.SET);
    }
}
