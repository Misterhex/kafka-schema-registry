package io.schemaregistry.mirror.storage;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.schemaregistry.mirror.schema.CompatibilityLevel;
import io.schemaregistry.mirror.storage.model.*;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.WakeupException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class KafkaStoreReaderThread extends Thread {

    private static final Logger log = LoggerFactory.getLogger(KafkaStoreReaderThread.class);

    private final KafkaConsumer<byte[], byte[]> consumer;
    private final String topic;
    private final InMemoryStore store;
    private final ObjectMapper objectMapper;
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final CountDownLatch initialLoadComplete = new CountDownLatch(1);
    private final AtomicLong lastWrittenOffset = new AtomicLong(-1);
    private volatile long offsetInSchemasTopic = -1;

    public KafkaStoreReaderThread(KafkaConsumer<byte[], byte[]> consumer, String topic,
                                  InMemoryStore store, ObjectMapper objectMapper) {
        super("kafka-store-reader");
        this.consumer = consumer;
        this.topic = topic;
        this.store = store;
        this.objectMapper = objectMapper;
        setDaemon(true);
    }

    @Override
    public void run() {
        try {
            TopicPartition topicPartition = new TopicPartition(topic, 0);
            consumer.assign(Collections.singletonList(topicPartition));
            consumer.seekToBeginning(Collections.singletonList(topicPartition));

            // Get the end offset to know when initial load is complete
            long endOffset = consumer.endOffsets(Collections.singletonList(topicPartition))
                .getOrDefault(topicPartition, 0L);

            log.info("Starting to read {} from beginning, end offset: {}", topic, endOffset);

            if (endOffset == 0) {
                log.info("Topic {} is empty, initial load complete", topic);
                initialLoadComplete.countDown();
            }

            while (running.get()) {
                try {
                    ConsumerRecords<byte[], byte[]> records = consumer.poll(Duration.ofMillis(1000));
                    for (ConsumerRecord<byte[], byte[]> record : records) {
                        processRecord(record);
                        offsetInSchemasTopic = record.offset();
                    }

                    // Check if we've caught up to the end offset (initial load)
                    if (initialLoadComplete.getCount() > 0 && offsetInSchemasTopic >= endOffset - 1) {
                        log.info("Initial load complete at offset {}", offsetInSchemasTopic);
                        initialLoadComplete.countDown();
                    }
                } catch (WakeupException e) {
                    // Expected on shutdown
                    if (!running.get()) break;
                }
            }
        } catch (Exception e) {
            log.error("Error in KafkaStoreReaderThread", e);
            // Ensure initial load completes (even if failed) to unblock waiters
            initialLoadComplete.countDown();
        }
    }

    private void processRecord(ConsumerRecord<byte[], byte[]> record) {
        try {
            if (record.key() == null) {
                log.warn("Ignoring record with null key at offset {}", record.offset());
                return;
            }

            SchemaRegistryKey key = objectMapper.readValue(record.key(), SchemaRegistryKey.class);

            if (key instanceof SchemaKey schemaKey) {
                if (record.value() == null) {
                    // Tombstone = hard delete
                    store.hardDelete(schemaKey.getSubject(), schemaKey.getVersion());
                } else {
                    SchemaValue value = objectMapper.readValue(record.value(), SchemaValue.class);
                    value.setOffset(record.offset());
                    value.setTimestamp(record.timestamp());
                    store.put(value);
                }
            } else if (key instanceof ConfigKey configKey) {
                if (record.value() == null) {
                    if (configKey.getSubject() != null && !configKey.getSubject().isEmpty()) {
                        store.deleteSubjectCompatibilityLevel(configKey.getSubject());
                    }
                } else {
                    ConfigValue value = objectMapper.readValue(record.value(), ConfigValue.class);
                    if (configKey.getSubject() != null && !configKey.getSubject().isEmpty()) {
                        store.setSubjectCompatibilityLevel(configKey.getSubject(), value.getCompatibilityLevel());
                    } else {
                        store.setGlobalCompatibilityLevel(value.getCompatibilityLevel());
                    }
                }
            } else if (key instanceof ModeKey modeKey) {
                if (record.value() == null) {
                    if (modeKey.getSubject() != null && !modeKey.getSubject().isEmpty()) {
                        store.deleteSubjectMode(modeKey.getSubject());
                    }
                } else {
                    ModeValue value = objectMapper.readValue(record.value(), ModeValue.class);
                    if (modeKey.getSubject() != null && !modeKey.getSubject().isEmpty()) {
                        store.setSubjectMode(modeKey.getSubject(), value.getMode());
                    } else {
                        store.setGlobalMode(value.getMode());
                    }
                }
            } else if (key instanceof DeleteSubjectKey deleteKey) {
                if (record.value() != null) {
                    store.softDeleteSubject(deleteKey.getSubject());
                }
            } else if (key instanceof ClearSubjectKey clearKey) {
                store.hardDeleteSubject(clearKey.getSubject());
            } else if (key instanceof NoopKey) {
                // No-op, used for leader election
            }

            lastWrittenOffset.set(record.offset());
        } catch (Exception e) {
            log.error("Error processing record at offset {}", record.offset(), e);
        }
    }

    public void waitForInitialLoad() throws InterruptedException {
        initialLoadComplete.await();
    }

    public boolean waitForInitialLoad(long timeoutMs) throws InterruptedException {
        return initialLoadComplete.await(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    public long getLastWrittenOffset() {
        return lastWrittenOffset.get();
    }

    public long getOffsetInSchemasTopic() {
        return offsetInSchemasTopic;
    }

    public void shutdown() {
        running.set(false);
        consumer.wakeup();
    }
}
