package io.schemaregistry.mirror.storage;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.confluent.kafka.schemaregistry.client.rest.entities.SchemaReference;
import io.schemaregistry.mirror.config.SchemaRegistryProperties;
import io.schemaregistry.mirror.exception.SchemaRegistryException;
import io.schemaregistry.mirror.schema.CompatibilityLevel;
import io.schemaregistry.mirror.storage.model.*;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.config.TopicConfig;
import org.apache.kafka.common.errors.TopicExistsException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

@Component
public class KafkaSchemaStore implements SchemaStore {

    private static final Logger log = LoggerFactory.getLogger(KafkaSchemaStore.class);

    private final SchemaRegistryProperties properties;
    private final AdminClient adminClient;
    private final KafkaProducer<byte[], byte[]> producer;
    private final KafkaConsumer<byte[], byte[]> consumer;
    private final ObjectMapper objectMapper;
    private final InMemoryStore store;
    private KafkaStoreReaderThread readerThread;
    private volatile boolean initialized = false;

    public KafkaSchemaStore(SchemaRegistryProperties properties,
                            AdminClient adminClient,
                            KafkaProducer<byte[], byte[]> producer,
                            KafkaConsumer<byte[], byte[]> consumer,
                            ObjectMapper objectMapper) {
        this.properties = properties;
        this.adminClient = adminClient;
        this.producer = producer;
        this.consumer = consumer;
        this.objectMapper = objectMapper;
        this.store = new InMemoryStore();

        // Set initial compatibility from config
        CompatibilityLevel defaultLevel;
        try {
            defaultLevel = CompatibilityLevel.forName(properties.getCompatibilityLevel());
        } catch (IllegalArgumentException e) {
            defaultLevel = CompatibilityLevel.BACKWARD;
        }
        store.setGlobalCompatibilityLevel(defaultLevel);
        store.setGlobalMode(properties.getMode());
    }

    @PostConstruct
    @Override
    public void start() throws Exception {
        createTopicIfNeeded();

        readerThread = new KafkaStoreReaderThread(consumer, properties.getTopic(), store, objectMapper);
        readerThread.start();

        boolean loaded = readerThread.waitForInitialLoad(properties.getInitTimeout());
        if (!loaded) {
            log.warn("Initial load did not complete within timeout");
        }

        initialized = true;
        log.info("KafkaSchemaStore initialized. Max schema ID: {}", store.getMaxSchemaId());
    }

    @PreDestroy
    @Override
    public void stop() {
        if (readerThread != null) {
            readerThread.shutdown();
            try {
                readerThread.join(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void createTopicIfNeeded() {
        String topicName = properties.getTopic();
        int replicationFactor = properties.getTopicReplicationFactor();

        Map<String, String> topicConfig = new HashMap<>();
        topicConfig.put(TopicConfig.CLEANUP_POLICY_CONFIG, TopicConfig.CLEANUP_POLICY_COMPACT);
        topicConfig.put(TopicConfig.UNCLEAN_LEADER_ELECTION_ENABLE_CONFIG, "false");

        NewTopic topic = new NewTopic(topicName, 1, (short) replicationFactor);
        topic.configs(topicConfig);

        try {
            adminClient.createTopics(Collections.singletonList(topic)).all().get(30, TimeUnit.SECONDS);
            log.info("Created topic {}", topicName);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof TopicExistsException) {
                log.info("Topic {} already exists", topicName);
            } else {
                log.error("Failed to create topic {}", topicName, e);
            }
        } catch (Exception e) {
            log.error("Failed to create topic {}", topicName, e);
        }
    }

    @Override
    public boolean initialized() {
        return initialized;
    }

    @Override
    public void waitForInit() throws InterruptedException {
        if (readerThread != null) {
            readerThread.waitForInitialLoad();
        }
    }

    // ---- Write to Kafka and wait for readback ----

    public void produce(SchemaRegistryKey key, SchemaRegistryValue value) {
        try {
            byte[] keyBytes = objectMapper.writeValueAsBytes(key);
            byte[] valueBytes = value != null ? objectMapper.writeValueAsBytes(value) : null;

            ProducerRecord<byte[], byte[]> record = new ProducerRecord<>(properties.getTopic(), 0, keyBytes, valueBytes);
            long offset = producer.send(record).get(properties.getKafkaStoreTimeoutMs(), TimeUnit.MILLISECONDS).offset();

            // Wait for reader thread to catch up
            waitForOffset(offset);
        } catch (Exception e) {
            throw SchemaRegistryException.storeException("Error writing to Kafka store", e);
        }
    }

    private void waitForOffset(long offset) throws InterruptedException {
        long startMs = System.currentTimeMillis();
        long timeoutMs = properties.getKafkaStoreTimeoutMs();
        while (readerThread.getLastWrittenOffset() < offset) {
            if (System.currentTimeMillis() - startMs > timeoutMs) {
                throw SchemaRegistryException.operationTimeoutException(
                    "Timed out waiting for store to catch up to offset " + offset);
            }
            Thread.sleep(10);
        }
    }

    // ---- Schema read operations (delegate to InMemoryStore) ----

    @Override
    public SchemaValue getSchemaById(int id) {
        return store.getSchemaById(id);
    }

    @Override
    public SchemaValue getSchema(String subject, int version, boolean lookupDeletedSchema) {
        return store.getSchema(subject, version, lookupDeletedSchema);
    }

    @Override
    public List<Integer> getVersions(String subject, boolean lookupDeletedSchema) {
        return store.getVersions(subject, lookupDeletedSchema);
    }

    @Override
    public List<String> getSubjects(boolean lookupDeletedSubjects) {
        return store.getSubjects(lookupDeletedSubjects);
    }

    @Override
    public int getLatestVersion(String subject, boolean lookupDeletedSchema) {
        return store.getLatestVersion(subject, lookupDeletedSchema);
    }

    @Override
    public boolean hasSubject(String subject, boolean lookupDeletedSubjects) {
        return store.hasSubject(subject, lookupDeletedSubjects);
    }

    @Override
    public List<SchemaValue> getSchemasBySubject(String subject, boolean lookupDeletedSchema) {
        return store.getSchemasBySubject(subject, lookupDeletedSchema);
    }

    @Override
    public SchemaValue lookupSchemaByContent(String subject, String schema, String schemaType,
                                             List<SchemaReference> references, boolean lookupDeletedSchema) {
        return store.lookupSchemaByContent(subject, schema, schemaType, references, lookupDeletedSchema);
    }

    @Override
    public List<String> getSubjectsForSchemaId(int id, boolean lookupDeletedSubjects) {
        return store.getSubjectsForSchemaId(id, lookupDeletedSubjects);
    }

    @Override
    public List<Map<String, Object>> getVersionsForSchemaId(int id, boolean lookupDeletedSubjects) {
        return store.getVersionsForSchemaId(id, lookupDeletedSubjects);
    }

    @Override
    public List<Integer> getReferencedBy(String subject, int version) {
        return store.getReferencedBy(subject, version);
    }

    @Override
    public int getMaxSchemaId() {
        return store.getMaxSchemaId();
    }

    // ---- Schema write operations ----

    @Override
    public void registerSchema(SchemaValue schemaValue) {
        SchemaKey key = schemaValue.toKey();
        produce(key, schemaValue);
    }

    @Override
    public void softDeleteSchema(String subject, int version) {
        SchemaValue existing = store.getSchema(subject, version, true);
        if (existing != null) {
            SchemaValue deleted = new SchemaValue(
                existing.getSubject(), existing.getVersion(), existing.getId(),
                existing.getMd5(), existing.getSchemaType(), existing.getReferences(),
                existing.getMetadata(), existing.getRuleSet(), existing.getSchema(), true
            );
            produce(deleted.toKey(), deleted);
        }
    }

    @Override
    public void hardDeleteSchema(String subject, int version) {
        SchemaKey key = new SchemaKey(subject, version);
        produce(key, null); // tombstone
    }

    @Override
    public void softDeleteSubject(String subject) {
        DeleteSubjectKey key = new DeleteSubjectKey(subject);
        int latestVersion = store.getLatestVersion(subject, true);
        DeleteSubjectValue value = new DeleteSubjectValue(subject, latestVersion);
        produce(key, value);
    }

    @Override
    public void hardDeleteSubject(String subject) {
        // Write tombstones for all versions
        List<Integer> versions = store.getVersions(subject, true);
        for (int version : versions) {
            SchemaKey key = new SchemaKey(subject, version);
            produce(key, null);
        }
        // Clear subject config
        ClearSubjectKey clearKey = new ClearSubjectKey(subject);
        ClearSubjectValue clearValue = new ClearSubjectValue(subject);
        produce(clearKey, clearValue);
    }

    // ---- Config operations ----

    @Override
    public CompatibilityLevel getGlobalCompatibilityLevel() {
        return store.getGlobalCompatibilityLevel();
    }

    @Override
    public void setGlobalCompatibilityLevel(CompatibilityLevel level) {
        ConfigKey key = new ConfigKey(null);
        ConfigValue value = new ConfigValue(null, level);
        produce(key, value);
    }

    @Override
    public CompatibilityLevel getSubjectCompatibilityLevel(String subject) {
        return store.getSubjectCompatibilityLevel(subject);
    }

    @Override
    public void setSubjectCompatibilityLevel(String subject, CompatibilityLevel level) {
        ConfigKey key = new ConfigKey(subject);
        ConfigValue value = new ConfigValue(subject, level);
        produce(key, value);
    }

    @Override
    public void deleteSubjectCompatibilityLevel(String subject) {
        ConfigKey key = new ConfigKey(subject);
        produce(key, null); // tombstone
    }

    @Override
    public boolean hasSubjectCompatibilityLevel(String subject) {
        return store.hasSubjectCompatibilityLevel(subject);
    }

    // ---- Mode operations ----

    @Override
    public String getGlobalMode() {
        return store.getGlobalMode();
    }

    @Override
    public void setGlobalMode(String mode) {
        ModeKey key = new ModeKey(null);
        ModeValue value = new ModeValue(null, mode);
        produce(key, value);
    }

    @Override
    public String getSubjectMode(String subject) {
        return store.getSubjectMode(subject);
    }

    @Override
    public void setSubjectMode(String subject, String mode) {
        ModeKey key = new ModeKey(subject);
        ModeValue value = new ModeValue(subject, mode);
        produce(key, value);
    }

    @Override
    public void deleteSubjectMode(String subject) {
        ModeKey key = new ModeKey(subject);
        produce(key, null); // tombstone
    }

    @Override
    public boolean hasSubjectMode(String subject) {
        return store.hasSubjectMode(subject);
    }

    public InMemoryStore getInMemoryStore() {
        return store;
    }
}
