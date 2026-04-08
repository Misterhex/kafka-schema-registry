package io.schemaregistry.mirror.leader;

import io.schemaregistry.mirror.config.SchemaRegistryProperties;
import io.schemaregistry.mirror.storage.KafkaSchemaStore;
import io.schemaregistry.mirror.storage.model.NoopKey;
import io.schemaregistry.mirror.storage.model.NoopValue;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Kafka-log-based leader election.
 *
 * <p>All instances replay the {@code _schemas} topic. A {@code NoopKey}
 * record carries a {@link LeaderIdentity} in its value. The rule is simple:
 * whoever wrote the most recent NoopKey seen in the log is the leader.
 * Because the topic has a single partition, every replica observes the same
 * total order and eventually agrees on the same leader.
 *
 * <p>This class runs a daemon thread that:
 * <ul>
 *   <li>On startup (after store init), if no leader is seen and this node is
 *       eligible, produces a NoopKey with {@code myIdentity}.</li>
 *   <li>If this node is the leader, heartbeats by re-producing a NoopKey
 *       every {@code heartbeatIntervalMs}.</li>
 *   <li>If this node is a follower but has not observed a heartbeat within
 *       {@code staleLeaderTimeoutMs}, attempts to take over by producing a
 *       NoopKey with its own identity.</li>
 * </ul>
 */
@Component
public class LeaderElector {

    private static final Logger log = LoggerFactory.getLogger(LeaderElector.class);

    private final SchemaRegistryProperties properties;
    private final KafkaSchemaStore store;
    private final LeaderState leaderState;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread thread;
    private volatile long lastHeartbeatMs = 0L;

    public LeaderElector(SchemaRegistryProperties properties,
                         KafkaSchemaStore store,
                         LeaderState leaderState) {
        this.properties = properties;
        this.store = store;
        this.leaderState = leaderState;
    }

    @PostConstruct
    public void start() {
        LeaderIdentity myIdentity = new LeaderIdentity(
                properties.getHostName(),
                properties.getInterInstancePort(),
                properties.getInterInstanceProtocol(),
                properties.isLeaderEligibility(),
                System.currentTimeMillis());
        leaderState.setMyIdentity(myIdentity);
        log.info("LeaderElector starting with identity {}", myIdentity);

        running.set(true);
        thread = new Thread(this::run, "leader-elector");
        thread.setDaemon(true);
        thread.start();
    }

    @PreDestroy
    public void stop() {
        running.set(false);
        if (thread != null) {
            thread.interrupt();
            try {
                thread.join(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        // If we are the leader on shutdown, publish a null-leader tombstone so
        // other eligible nodes can take over immediately.
        try {
            if (leaderState.isLeader()) {
                store.produce(new NoopKey(), null);
                log.info("Published leader tombstone on shutdown");
            }
        } catch (Exception e) {
            log.warn("Failed to publish leader tombstone on shutdown: {}", e.getMessage());
        }
    }

    private void run() {
        try {
            // Wait for the reader thread to finish the initial bootstrap so we
            // know whether there is already a leader before running the first
            // election pass.
            store.waitForInit();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }

        long heartbeatMs = properties.getLeaderHeartbeatIntervalMs();
        long staleMs = properties.getLeaderStaleTimeoutMs();
        long tickMs = Math.max(50L, heartbeatMs / 3);

        // Initial election attempt: if we are eligible and there is no current
        // leader, try to become the leader.
        maybeTakeOver(staleMs);

        while (running.get()) {
            try {
                Thread.sleep(tickMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }

            long now = System.currentTimeMillis();
            if (leaderState.isLeader()) {
                if (now - lastHeartbeatMs >= heartbeatMs) {
                    try {
                        publishLeader();
                        lastHeartbeatMs = now;
                    } catch (Exception e) {
                        log.warn("Leader heartbeat failed: {}", e.getMessage());
                    }
                }
            } else {
                maybeTakeOver(staleMs);
            }
        }
    }

    private void maybeTakeOver(long staleMs) {
        if (!properties.isLeaderEligibility()) {
            return;
        }
        LeaderIdentity current = leaderState.getCurrentLeader();
        long lastSeen = leaderState.getLastNoopObservedAtMs();
        long now = System.currentTimeMillis();
        boolean stale = current == null || (now - lastSeen) > staleMs;
        if (stale) {
            try {
                log.info("No live leader observed (current={}, age={}ms); attempting take-over",
                        current, (current == null ? -1 : now - lastSeen));
                publishLeader();
                lastHeartbeatMs = now;
            } catch (Exception e) {
                log.warn("Take-over attempt failed: {}", e.getMessage());
            }
        }
    }

    private void publishLeader() {
        LeaderIdentity myIdentity = leaderState.getMyIdentity();
        NoopValue value = new NoopValue(new LeaderIdentity(
                myIdentity.getHost(),
                myIdentity.getPort(),
                myIdentity.getScheme(),
                myIdentity.isLeaderEligible(),
                System.currentTimeMillis()));
        store.produce(new NoopKey(), value);
    }
}
