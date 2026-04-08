package io.schemaregistry.mirror.leader;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

/**
 * Identity of a schema registry instance participating in leader election.
 * Composed of the host, port, and scheme the instance advertises to peers on
 * the inter-instance protocol.
 */
public final class LeaderIdentity {

    private final String host;
    private final int port;
    private final String scheme;
    private final boolean leaderEligible;
    private final long version;

    @JsonCreator
    public LeaderIdentity(
            @JsonProperty("host") String host,
            @JsonProperty("port") int port,
            @JsonProperty("scheme") String scheme,
            @JsonProperty("leader_eligibility") boolean leaderEligible,
            @JsonProperty("version") long version) {
        this.host = host;
        this.port = port;
        this.scheme = scheme == null ? "http" : scheme;
        this.leaderEligible = leaderEligible;
        this.version = version;
    }

    @JsonProperty("host")
    public String getHost() {
        return host;
    }

    @JsonProperty("port")
    public int getPort() {
        return port;
    }

    @JsonProperty("scheme")
    public String getScheme() {
        return scheme;
    }

    @JsonProperty("leader_eligibility")
    public boolean isLeaderEligible() {
        return leaderEligible;
    }

    @JsonProperty("version")
    public long getVersion() {
        return version;
    }

    public String url() {
        return scheme + "://" + host + ":" + port;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof LeaderIdentity)) return false;
        LeaderIdentity that = (LeaderIdentity) o;
        return port == that.port
                && leaderEligible == that.leaderEligible
                && version == that.version
                && Objects.equals(host, that.host)
                && Objects.equals(scheme, that.scheme);
    }

    @Override
    public int hashCode() {
        return Objects.hash(host, port, scheme, leaderEligible, version);
    }

    @Override
    public String toString() {
        return "LeaderIdentity{" + url() + ",eligible=" + leaderEligible + ",version=" + version + "}";
    }
}
