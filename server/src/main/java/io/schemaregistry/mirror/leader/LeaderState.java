package io.schemaregistry.mirror.leader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Observable state of the schema registry leader as seen from this instance.
 *
 * <p>Updated by {@code KafkaStoreReaderThread} whenever it observes a
 * {@code NoopKey}/{@code NoopValue} record on the {@code _schemas} topic, and
 * read by {@code LeaderElector} (to decide whether to heartbeat / try to take
 * over) and by {@code LeaderForwardingFilter} (to decide whether to handle
 * the request locally or forward it to the current leader).
 *
 * <p>We also track the offset and wall-clock timestamp of the last observed
 * Noop record so the elector can detect a stale leader.
 */
@Component
public class LeaderState {

    private static final Logger log = LoggerFactory.getLogger(LeaderState.class);

    private final AtomicReference<LeaderIdentity> currentLeader = new AtomicReference<>();
    private volatile long lastNoopOffset = -1L;
    private volatile long lastNoopObservedAtMs = 0L;
    private volatile LeaderIdentity myIdentity;

    public void setMyIdentity(LeaderIdentity identity) {
        this.myIdentity = identity;
    }

    public LeaderIdentity getMyIdentity() {
        return myIdentity;
    }

    public LeaderIdentity getCurrentLeader() {
        return currentLeader.get();
    }

    public long getLastNoopOffset() {
        return lastNoopOffset;
    }

    public long getLastNoopObservedAtMs() {
        return lastNoopObservedAtMs;
    }

    public boolean isLeader() {
        LeaderIdentity leader = currentLeader.get();
        LeaderIdentity me = myIdentity;
        return leader != null && me != null
                && leader.getHost().equals(me.getHost())
                && leader.getPort() == me.getPort();
    }

    /**
     * Called by the store reader thread when a NoopKey record is consumed.
     * {@code leader} may be {@code null} to indicate a tombstone ("no leader").
     */
    public void observeLeader(LeaderIdentity leader, long offset) {
        LeaderIdentity prev = currentLeader.getAndSet(leader);
        lastNoopOffset = offset;
        lastNoopObservedAtMs = System.currentTimeMillis();
        if (prev == null || !prev.equals(leader)) {
            if (leader == null) {
                log.info("Leader cleared (tombstone) at offset {}", offset);
            } else {
                log.info("Leader updated to {} at offset {}", leader, offset);
            }
        }
    }
}
