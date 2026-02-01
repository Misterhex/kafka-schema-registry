package io.schemaregistry.mirror.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "schema.registry")
public class SchemaRegistryProperties {

    private String kafkaBootstrapServers = "localhost:9092";
    private String topic = "_schemas";
    private int topicReplicationFactor = 1;
    private String groupId = "schema-registry-mirror";
    private String compatibilityLevel = "BACKWARD";
    private String mode = "READWRITE";
    private String host = "localhost";
    private int port = 8081;
    private long initTimeout = 60000;
    private long kafkaStoreTimeoutMs = 500;

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

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
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
}
