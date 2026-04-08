package io.schemaregistry.mirror.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "schema.registry")
public class SchemaRegistryProperties {

    // --- Kafka storage ---
    private String kafkaBootstrapServers = "localhost:9092";
    private String topic = "_schemas";
    private int topicReplicationFactor = 3;
    private String groupId = "schema-registry";
    private long initTimeout = 60000;
    private long kafkaStoreTimeoutMs = 500;

    // --- Compatibility / mode defaults ---
    private String compatibilityLevel = "BACKWARD";
    private String mode = "READWRITE";
    private boolean modeMutability = true;

    // --- Listener and identity ---
    /** Hostname advertised to peers for inter-instance forwarding. */
    private String hostName = "localhost";
    /** Scheme for inter-instance forwarding (http/https). */
    private String interInstanceProtocol = "http";
    /** Port advertised to peers. Defaults to the server port if -1. */
    private int interInstancePort = -1;

    // --- Leader election / forwarding ---
    private boolean leaderEligibility = true;
    private long leaderHeartbeatIntervalMs = 2000;
    private long leaderStaleTimeoutMs = 6000;
    /** Hard cap on forwarding hops (safety net). */
    private int maxForwardingHops = 1;
    /** Connect/read timeout for forwarded HTTP calls. */
    private long forwardingRequestTimeoutMs = 30000;

    // --- Auth ---
    private Auth auth = new Auth();

    public String getKafkaBootstrapServers() {
        return kafkaBootstrapServers;
    }

    public void setKafkaBootstrapServers(String kafkaBootstrapServers) {
        this.kafkaBootstrapServers = kafkaBootstrapServers;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public int getTopicReplicationFactor() {
        return topicReplicationFactor;
    }

    public void setTopicReplicationFactor(int topicReplicationFactor) {
        this.topicReplicationFactor = topicReplicationFactor;
    }

    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    public String getCompatibilityLevel() {
        return compatibilityLevel;
    }

    public void setCompatibilityLevel(String compatibilityLevel) {
        this.compatibilityLevel = compatibilityLevel;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public boolean isModeMutability() {
        return modeMutability;
    }

    public void setModeMutability(boolean modeMutability) {
        this.modeMutability = modeMutability;
    }

    public String getHostName() {
        return hostName;
    }

    public void setHostName(String hostName) {
        this.hostName = hostName;
    }

    public String getInterInstanceProtocol() {
        return interInstanceProtocol;
    }

    public void setInterInstanceProtocol(String interInstanceProtocol) {
        this.interInstanceProtocol = interInstanceProtocol;
    }

    public int getInterInstancePort() {
        return interInstancePort;
    }

    public void setInterInstancePort(int interInstancePort) {
        this.interInstancePort = interInstancePort;
    }

    public boolean isLeaderEligibility() {
        return leaderEligibility;
    }

    public void setLeaderEligibility(boolean leaderEligibility) {
        this.leaderEligibility = leaderEligibility;
    }

    public long getLeaderHeartbeatIntervalMs() {
        return leaderHeartbeatIntervalMs;
    }

    public void setLeaderHeartbeatIntervalMs(long leaderHeartbeatIntervalMs) {
        this.leaderHeartbeatIntervalMs = leaderHeartbeatIntervalMs;
    }

    public long getLeaderStaleTimeoutMs() {
        return leaderStaleTimeoutMs;
    }

    public void setLeaderStaleTimeoutMs(long leaderStaleTimeoutMs) {
        this.leaderStaleTimeoutMs = leaderStaleTimeoutMs;
    }

    public int getMaxForwardingHops() {
        return maxForwardingHops;
    }

    public void setMaxForwardingHops(int maxForwardingHops) {
        this.maxForwardingHops = maxForwardingHops;
    }

    public long getForwardingRequestTimeoutMs() {
        return forwardingRequestTimeoutMs;
    }

    public void setForwardingRequestTimeoutMs(long forwardingRequestTimeoutMs) {
        this.forwardingRequestTimeoutMs = forwardingRequestTimeoutMs;
    }

    public long getInitTimeout() {
        return initTimeout;
    }

    public void setInitTimeout(long initTimeout) {
        this.initTimeout = initTimeout;
    }

    public long getKafkaStoreTimeoutMs() {
        return kafkaStoreTimeoutMs;
    }

    public void setKafkaStoreTimeoutMs(long kafkaStoreTimeoutMs) {
        this.kafkaStoreTimeoutMs = kafkaStoreTimeoutMs;
    }

    public Auth getAuth() {
        return auth;
    }

    public void setAuth(Auth auth) {
        this.auth = auth;
    }

    public static class Auth {
        private String username = "admin";
        private String password;

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }
}
