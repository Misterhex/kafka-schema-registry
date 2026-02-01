package io.schemaregistry.mirror.schema;

import com.fasterxml.jackson.annotation.JsonValue;

public enum CompatibilityLevel {

    NONE("NONE"),
    BACKWARD("BACKWARD"),
    BACKWARD_TRANSITIVE("BACKWARD_TRANSITIVE"),
    FORWARD("FORWARD"),
    FORWARD_TRANSITIVE("FORWARD_TRANSITIVE"),
    FULL("FULL"),
    FULL_TRANSITIVE("FULL_TRANSITIVE");

    private final String name;

    CompatibilityLevel(String name) {
        this.name = name;
    }

    @JsonValue
    public String getName() {
        return name;
    }

    public static CompatibilityLevel forName(String name) {
        if (name == null) return null;
        for (CompatibilityLevel level : values()) {
            if (level.name.equalsIgnoreCase(name)) {
                return level;
            }
        }
        throw new IllegalArgumentException("Invalid compatibility level: " + name);
    }

    @Override
    public String toString() {
        return name;
    }
}
