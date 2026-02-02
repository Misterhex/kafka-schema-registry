package io.schemaregistry.abtest.comparator;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import io.schemaregistry.abtest.model.TestResult.MatchStatus;
import io.schemaregistry.abtest.runner.HttpExecutor.Response;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.TreeSet;

@Component
public class ResponseComparator {

    private final ObjectMapper mapper;

    public ResponseComparator() {
        this.mapper = new ObjectMapper();
        this.mapper.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
    }

    public record ComparisonResult(MatchStatus status, String detail) {}

    public ComparisonResult compare(Response confluent, Response mirror, ComparisonMode mode) {
        return compare(confluent, mirror, mode, null);
    }

    public ComparisonResult compare(Response confluent, Response mirror, ComparisonMode mode, String[] fields) {
        if (confluent.status() != mirror.status()) {
            return new ComparisonResult(MatchStatus.DIFF,
                    "Status mismatch: confluent=%d mirror=%d".formatted(confluent.status(), mirror.status()));
        }

        return switch (mode) {
            case EXACT -> compareExact(confluent.body(), mirror.body());
            case JSON_STRUCTURE -> compareJsonStructure(confluent.body(), mirror.body(), fields);
            case SET -> compareSet(confluent.body(), mirror.body());
            case STATUS_AND_ERROR_CODE -> compareStatusAndErrorCode(confluent.body(), mirror.body());
            case STRUCTURE_ONLY -> compareStructureOnly(confluent.body(), mirror.body());
        };
    }

    private ComparisonResult compareExact(String confluentBody, String mirrorBody) {
        try {
            JsonNode confluentJson = mapper.readTree(confluentBody);
            JsonNode mirrorJson = mapper.readTree(mirrorBody);

            String confluentSorted = mapper.writeValueAsString(mapper.treeToValue(confluentJson, Object.class));
            String mirrorSorted = mapper.writeValueAsString(mapper.treeToValue(mirrorJson, Object.class));

            if (confluentSorted.equals(mirrorSorted)) {
                return new ComparisonResult(MatchStatus.MATCH, null);
            }
            return new ComparisonResult(MatchStatus.DIFF,
                    "Body mismatch: confluent=%s mirror=%s".formatted(confluentBody, mirrorBody));
        } catch (JsonProcessingException e) {
            if (confluentBody != null && confluentBody.equals(mirrorBody)) {
                return new ComparisonResult(MatchStatus.MATCH, null);
            }
            return new ComparisonResult(MatchStatus.DIFF,
                    "Body mismatch (non-JSON): confluent=%s mirror=%s".formatted(confluentBody, mirrorBody));
        }
    }

    private ComparisonResult compareJsonStructure(String confluentBody, String mirrorBody, String[] fields) {
        try {
            JsonNode confluentJson = mapper.readTree(confluentBody);
            JsonNode mirrorJson = mapper.readTree(mirrorBody);

            if (fields == null || fields.length == 0) {
                return compareExact(confluentBody, mirrorBody);
            }

            for (String field : fields) {
                JsonNode cVal = confluentJson.get(field);
                JsonNode mVal = mirrorJson.get(field);
                if (cVal == null && mVal == null) continue;
                if (cVal == null || mVal == null) {
                    return new ComparisonResult(MatchStatus.DIFF,
                            "Field '%s' missing in one response".formatted(field));
                }
                if (!cVal.equals(mVal)) {
                    return new ComparisonResult(MatchStatus.DIFF,
                            "Field '%s' mismatch: confluent=%s mirror=%s".formatted(field, cVal, mVal));
                }
            }
            return new ComparisonResult(MatchStatus.MATCH, null);
        } catch (JsonProcessingException e) {
            return new ComparisonResult(MatchStatus.DIFF, "Failed to parse JSON: " + e.getMessage());
        }
    }

    private ComparisonResult compareSet(String confluentBody, String mirrorBody) {
        try {
            JsonNode confluentJson = mapper.readTree(confluentBody);
            JsonNode mirrorJson = mapper.readTree(mirrorBody);

            if (confluentJson.isArray() && mirrorJson.isArray()) {
                Set<String> confluentSet = arrayToSet((ArrayNode) confluentJson);
                Set<String> mirrorSet = arrayToSet((ArrayNode) mirrorJson);
                if (confluentSet.equals(mirrorSet)) {
                    return new ComparisonResult(MatchStatus.MATCH, null);
                }
                return new ComparisonResult(MatchStatus.DIFF,
                        "Set mismatch: confluent=%s mirror=%s".formatted(confluentSet, mirrorSet));
            }
            return compareExact(confluentBody, mirrorBody);
        } catch (JsonProcessingException e) {
            return new ComparisonResult(MatchStatus.DIFF, "Failed to parse JSON: " + e.getMessage());
        }
    }

    private ComparisonResult compareStatusAndErrorCode(String confluentBody, String mirrorBody) {
        try {
            JsonNode confluentJson = mapper.readTree(confluentBody);
            JsonNode mirrorJson = mapper.readTree(mirrorBody);

            JsonNode cCode = confluentJson.get("error_code");
            JsonNode mCode = mirrorJson.get("error_code");

            if (cCode != null && mCode != null && cCode.equals(mCode)) {
                return new ComparisonResult(MatchStatus.MATCH, null);
            }
            if (cCode == null && mCode == null) {
                return new ComparisonResult(MatchStatus.MATCH, null);
            }
            return new ComparisonResult(MatchStatus.DIFF,
                    "Error code mismatch: confluent=%s mirror=%s".formatted(cCode, mCode));
        } catch (JsonProcessingException e) {
            return new ComparisonResult(MatchStatus.DIFF, "Failed to parse JSON: " + e.getMessage());
        }
    }

    private ComparisonResult compareStructureOnly(String confluentBody, String mirrorBody) {
        try {
            JsonNode confluentJson = mapper.readTree(confluentBody);
            JsonNode mirrorJson = mapper.readTree(mirrorBody);

            Set<String> confluentKeys = new TreeSet<>();
            Set<String> mirrorKeys = new TreeSet<>();
            confluentJson.fieldNames().forEachRemaining(confluentKeys::add);
            mirrorJson.fieldNames().forEachRemaining(mirrorKeys::add);

            if (confluentKeys.equals(mirrorKeys)) {
                return new ComparisonResult(MatchStatus.STRUCTURAL, null);
            }
            return new ComparisonResult(MatchStatus.DIFF,
                    "Structure mismatch: confluent keys=%s mirror keys=%s".formatted(confluentKeys, mirrorKeys));
        } catch (JsonProcessingException e) {
            return new ComparisonResult(MatchStatus.DIFF, "Failed to parse JSON: " + e.getMessage());
        }
    }

    private Set<String> arrayToSet(ArrayNode array) {
        Set<String> set = new HashSet<>();
        for (Iterator<JsonNode> it = array.elements(); it.hasNext(); ) {
            try {
                set.add(mapper.writeValueAsString(mapper.treeToValue(it.next(), Object.class)));
            } catch (JsonProcessingException e) {
                set.add(it.toString());
            }
        }
        return set;
    }
}
