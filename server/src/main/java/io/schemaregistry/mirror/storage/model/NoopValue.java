package io.schemaregistry.mirror.storage.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.schemaregistry.mirror.leader.LeaderIdentity;

/**
 * Value written alongside a {@link NoopKey} on the {@code _schemas} topic to
 * announce the current leader identity. All instances replay the topic and
 * use the last NoopValue they see as the current leader.
 *
 * <p>A {@code null} NoopValue (tombstone) represents "no leader"; any
 * eligible follower should then try to become leader.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonIgnoreProperties(ignoreUnknown = true)
public class NoopValue extends SchemaRegistryValue {

    private LeaderIdentity leader;

    public NoopValue() {
    }

    public NoopValue(LeaderIdentity leader) {
        this.leader = leader;
    }

    @JsonProperty("leader")
    public LeaderIdentity getLeader() {
        return leader;
    }

    @JsonProperty("leader")
    public void setLeader(LeaderIdentity leader) {
        this.leader = leader;
    }

    @Override
    public SchemaRegistryKey toKey() {
        return new NoopKey();
    }
}
