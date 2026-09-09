package com.akkc.tensor.core.download;

import static org.assertj.core.api.Assertions.*;
import com.akkc.tensor.plugin.api.error.ErrorCode;
import com.akkc.tensor.plugin.api.error.TensorException;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;

class DownloadExecutionSlotTest {
    @Test void ownsOneLeaseAndOnlyItsThreadCanReleaseIt() throws Exception {
        var slot = new DownloadExecutionSlot();
        var id = UUID.randomUUID();
        var lease = slot.acquire(id);
        assertThat(slot.snapshot().busy()).isTrue();
        assertThat(slot.snapshot().retryTaskId()).isEqualTo(id);
        assertThat(slot.busy()).isTrue();
        assertThat(slot.retrying(id)).isTrue();
        assertThat(slot.retrying(null)).isFalse();
        assertThat(slot.retrying(UUID.randomUUID())).isFalse();
        try (var worker = Executors.newSingleThreadExecutor()) {
            worker.submit(() -> {
                assertThatThrownBy(lease::close).isInstanceOf(IllegalStateException.class);
                assertThatThrownBy(() -> slot.acquire(null)).isInstanceOfSatisfying(TensorException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.DOWNLOAD_BUSY));
            }).get(5, TimeUnit.SECONDS);
        }
        lease.close();
        assertThat(slot.busy()).isFalse();
        try (var next = slot.acquire(null)) {
            lease.close();
            assertThat(slot.busy()).isTrue();
            assertThat(slot.retrying(id)).isFalse();
        }
        assertThat(slot.busy()).isFalse();
    }
}
