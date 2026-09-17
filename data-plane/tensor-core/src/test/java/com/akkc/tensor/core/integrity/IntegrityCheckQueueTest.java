package com.akkc.tensor.core.integrity;

import static org.assertj.core.api.Assertions.*;
import static com.akkc.tensor.core.integrity.IntegrityCheckServiceTest.rejects;
import com.akkc.tensor.plugin.api.error.ErrorCode;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;

class IntegrityCheckQueueTest {
    @Test void capacityOnePublicationCloseAndConsumptionRacesReleaseExactlyOnePermit() throws Exception {
        var queue = new IntegrityCheckQueue(1);
        try (var executor = Executors.newFixedThreadPool(3)) {
            for (int i = 0; i < 30; i++) {
                var slot = queue.reserve(); var start = new CountDownLatch(1); var id = UUID.randomUUID();
                var publish = executor.submit(() -> {
                    start.await();
                    try { slot.publish(id); return true; } catch (IllegalStateException closed) { return false; }
                });
                var close = executor.submit(() -> { start.await(); slot.close(); return null; });
                var consume = executor.submit(() -> { start.await(); return queue.poll(Duration.ofMillis(10)); });
                start.countDown(); close.get(5, TimeUnit.SECONDS);
                boolean published = publish.get(5, TimeUnit.SECONDS);
                var observed = consume.get(5, TimeUnit.SECONDS);
                if (observed.isEmpty()) observed = queue.poll(Duration.ZERO);
                assertThat(observed).isEqualTo(published ? Optional.of(id) : Optional.empty());
                try (var next = queue.reserve()) { rejects(queue::reserve, ErrorCode.INTEGRITY_QUEUE_FULL); }
            }
        }
    }
    @Test void reservationsRemainInvisibleAndCloseReleasesExactlyOnce() throws Exception {
        var queue = new IntegrityCheckQueue(1);
        var slot = queue.reserve();
        assertThat(queue.poll(Duration.ZERO)).isEmpty();
        rejects(queue::reserve, ErrorCode.INTEGRITY_QUEUE_FULL);
        slot.close(); slot.close();
        try (var next = queue.reserve()) {
            rejects(queue::reserve, ErrorCode.INTEGRITY_QUEUE_FULL);
            assertThatThrownBy(() -> slot.publish(UUID.randomUUID())).isInstanceOf(IllegalStateException.class);
        }
    }

    @Test void publishedSlotIsHeldUntilConsumedAndCannotPublishTwice() throws Exception {
        var queue = new IntegrityCheckQueue(1); var id = UUID.randomUUID();
        try (var slot = queue.reserve()) {
            slot.publish(id);
            assertThatThrownBy(() -> slot.publish(id)).isInstanceOf(IllegalStateException.class);
        }
        rejects(queue::reserve, ErrorCode.INTEGRITY_QUEUE_FULL);
        assertThat(queue.poll(Duration.ZERO)).contains(id);
        assertThat(queue.poll(Duration.ofMillis(1))).isEmpty();
        try (var next = queue.reserve()) { rejects(queue::reserve, ErrorCode.INTEGRITY_QUEUE_FULL); }
    }

    @Test void interruptedPollDoesNotReleaseAnUnconsumedReservation() {
        var queue = new IntegrityCheckQueue(1);
        try (var slot = queue.reserve()) {
            Thread.currentThread().interrupt();
            assertThatThrownBy(() -> queue.poll(Duration.ofSeconds(1))).isInstanceOf(InterruptedException.class);
            assertThat(Thread.interrupted()).isFalse();
            rejects(queue::reserve, ErrorCode.INTEGRITY_QUEUE_FULL);
        }
    }

    @Test void concurrentReservationsCannotExceedCapacity() throws Exception {
        var queue = new IntegrityCheckQueue(20);
        try (var executor = Executors.newFixedThreadPool(8)) {
            var tasks = new ArrayList<Future<Boolean>>();
            for (int i = 0; i < 40; i++) tasks.add(executor.submit(() -> {
                try (var slot = queue.reserve()) { slot.publish(UUID.randomUUID()); return true; }
                catch (com.akkc.tensor.plugin.api.error.TensorException e) {
                    assertThat(e.code()).isEqualTo(ErrorCode.INTEGRITY_QUEUE_FULL); return false;
                }
            }));
            int accepted = 0;
            for (var task : tasks) if (task.get(5, TimeUnit.SECONDS)) accepted++;
            assertThat(accepted).isEqualTo(20);
        }
        var ids = new HashSet<UUID>();
        for (int i = 0; i < 20; i++) ids.add(queue.poll(Duration.ZERO).orElseThrow());
        assertThat(ids).hasSize(20);
        assertThat(queue.poll(Duration.ZERO)).isEmpty();
    }
}
