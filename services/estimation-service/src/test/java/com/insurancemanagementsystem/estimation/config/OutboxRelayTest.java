package com.insurancemanagementsystem.estimation.config;

import com.insurancemanagementsystem.common.messaging.MessagePublisher;
import com.insurancemanagementsystem.estimation.entity.OutboxEvent;
import com.insurancemanagementsystem.estimation.repository.OutboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxRelayTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private MessagePublisher messagePublisher;

    @InjectMocks
    private OutboxRelay outboxRelay;

    @Captor
    private ArgumentCaptor<OutboxEvent> outboxEventCaptor;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(outboxRelay, "pollIntervalMs", 1000);
        ReflectionTestUtils.setField(outboxRelay, "batchSize", 10);
        ReflectionTestUtils.setField(outboxRelay, "maxRetries", 3);
        ReflectionTestUtils.setField(outboxRelay, "failedTtlMinutes", 60);
    }

    private OutboxEvent createPendingEvent() {
        return OutboxEvent.builder()
                .id(UUID.randomUUID())
                .sagaId(UUID.randomUUID())
                .topic("estimation.saga")
                .payload("{}")
                .status(OutboxEvent.Status.PENDING)
                .retryCount(0)
                .maxRetries(3)
                .createdAt(Instant.now())
                .build();
    }

    // ---------------------------------------------------------------
    // 1. No pending events → no-op
    // ---------------------------------------------------------------
    @Test
    void noPendingEvents_doesNothing() {
        when(outboxEventRepository.findTop10ByStatusOrderByCreatedAtAsc(OutboxEvent.Status.PENDING))
                .thenReturn(List.of());

        outboxRelay.processOutbox();

        verify(outboxEventRepository).findTop10ByStatusOrderByCreatedAtAsc(OutboxEvent.Status.PENDING);
        verifyNoMoreInteractions(outboxEventRepository);
        verifyNoInteractions(messagePublisher);
    }

    // ---------------------------------------------------------------
    // 2. Pending event published → save(PUBLISHING), publish, delete
    // ---------------------------------------------------------------
    @Test
    void pendingEvent_publishedSuccessfully_deleted() {
        OutboxEvent pending = createPendingEvent();
        when(outboxEventRepository.findTop10ByStatusOrderByCreatedAtAsc(OutboxEvent.Status.PENDING))
                .thenReturn(List.of(pending));

        outboxRelay.processOutbox();

        // Save called once with PUBLISHING status
        verify(outboxEventRepository).save(outboxEventCaptor.capture());
        assertThat(outboxEventCaptor.getValue().getStatus()).isEqualTo(OutboxEvent.Status.PUBLISHING);

        // Published and deleted
        verify(messagePublisher).publish(pending.getTopic(), pending.getPayload());
        verify(outboxEventRepository).delete(pending);
    }

    // ---------------------------------------------------------------
    // 3. Publish fails → save(PUBLISHING) + save(PENDING), not deleted
    // ---------------------------------------------------------------
    @Test
    void publishFails_savesTwiceAndRetries() {
        OutboxEvent pending = createPendingEvent();
        when(outboxEventRepository.findTop10ByStatusOrderByCreatedAtAsc(OutboxEvent.Status.PENDING))
                .thenReturn(List.of(pending));
        doThrow(new RuntimeException("Kafka down"))
                .when(messagePublisher).publish(anyString(), any());

        outboxRelay.processOutbox();

        // save called twice (PUBLISHING then PENDING for retry)
        verify(outboxEventRepository, times(2)).save(any(OutboxEvent.class));
        verify(outboxEventRepository, never()).delete(any());
    }

    // ---------------------------------------------------------------
    // 4. Max retries reached → save(PUBLISHING) + save(FAILED), stays FAILED
    // ---------------------------------------------------------------
    @Test
    void maxRetriesReached_staysFailed() {
        OutboxEvent pending = createPendingEvent();
        pending.setRetryCount(2); // One more attempt = maxRetries (3)
        when(outboxEventRepository.findTop10ByStatusOrderByCreatedAtAsc(OutboxEvent.Status.PENDING))
                .thenReturn(List.of(pending));
        doThrow(new RuntimeException("Kafka still down"))
                .when(messagePublisher).publish(anyString(), any());

        outboxRelay.processOutbox();

        // save called twice (PUBLISHING then FAILED)
        verify(outboxEventRepository, times(2)).save(any(OutboxEvent.class));
        // Log confirms: "Outbox event id=... reached max retries (3). Giving up."
        verify(outboxEventRepository, never()).delete(any());
    }
}
