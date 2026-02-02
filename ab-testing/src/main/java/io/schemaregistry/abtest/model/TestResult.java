package io.schemaregistry.abtest.model;

import io.schemaregistry.abtest.comparator.ComparisonMode;

public record TestResult(
        int number,
        String phase,
        String name,
        String method,
        String endpoint,
        String requestBody,
        int confluentStatus,
        String confluentBody,
        String confluentContentType,
        int mirrorStatus,
        String mirrorBody,
        String mirrorContentType,
        ComparisonMode comparisonMode,
        MatchStatus status,
        String diffDetail
) {
    public enum MatchStatus {
        MATCH, DIFF, STRUCTURAL
    }
}
