package com.insurancemanagementsystem.customer.config;

import com.insurancemanagementsystem.common.messaging.MessagePublisher;
import com.insurancemanagementsystem.customer.entity.OutboxEvent;
import com.insurancemanagementsystem.customer.repository.OutboxEventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxProcessorTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private MessagePublisher messagePublisher;

    @Mock
    private TransactionTemplate transactionTemplate;

    @InjectMocks
    private OutboxProcessor outboxProcessor;

    /**
     * Helper: make transactionTemplate.executeWithoutResult() execute the callback immediately.
     */
    private void mockTransaction() {
        doAnswer(invocation -> {
            Consumer<?> action = invocation.getArgument(0);
            action.accept(null);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());
    }

    private OutboxEvent createEvent(OutboxEvent.Status status) {
        return OutboxEvent.builder()
                .id(UUID.randomUUID())
                .sagaId(UUID.randomUUID())
                .topic("estimation.saga")
                .payload("{}")
                .status(status)
                .retryCount(0)
                .maxRetries(3)
                .createdAt(Instant.now().minusSeconds(600))
                .build();
    }

    private OutboxEvent createPendingEvent() {
        return createEvent(OutboxEvent.Status.PENDING);
    }

    // ---------------------------------------------------------------
    // 1. No pending events → no-op
    // ---------------------------------------------------------------
    @Test
    void noPendingEvents_doesNothing() {
        mockTransaction();
        when(outboxEventRepository.findTop10ByStatusOrderByCreatedAtAsc(OutboxEvent.Status.PENDING))
                .thenReturn(List.of());

        outboxProcessor.processOutbox();

        verify(outboxEventRepository).findTop10ByStatusOrderByCreatedAtAsc(OutboxEvent.Status.PENDING);
        verify(outboxEventRepository, never()).save(any());
        verifyNoInteractions(messagePublisher);
    }

    // ---------------------------------------------------------------
    // 2. Pending event published → save*2 → publish → final status PUBLISHED
    // ---------------------------------------------------------------
    @Test
    void pendingEvent_publishedSuccessfully() {
        mockTransaction();
        OutboxEvent pending = createPendingEvent();
        when(outboxEventRepository.findTop10ByStatusOrderByCreatedAtAsc(OutboxEvent.Status.PENDING))
                .thenReturn(List.of(pending));

        outboxProcessor.processOutbox();

        // save called twice (PUBLISHING + PUBLISHED)
        verify(outboxEventRepository, times(2)).save(same(pending));
        verify(messagePublisher).publish(pending.getTopic(), pending.getPayload());
        verify(outboxEventRepository, never()).delete(any());

        // Final state on the entity is PUBLISHED
        assertThat(pending.getStatus()).isEqualTo(OutboxEvent.Status.PUBLISHED);
    }

    // ---------------------------------------------------------------
    // 3. Publish fails → save(PUBLISHING) + save(PENDING), not deleted
    // ---------------------------------------------------------------
    @Test
    void publishFails_savesTwiceAndRetries() {
        mockTransaction();
        OutboxEvent pending = createPendingEvent();
        when(outboxEventRepository.findTop10ByStatusOrderByCreatedAtAsc(OutboxEvent.Status.PENDING))
                .thenReturn(List.of(pending));
        doThrow(new RuntimeException("Kafka down"))
                .when(messagePublisher).publish(anyString(), any());

        outboxProcessor.processOutbox();

        // save called twice (PUBLISHING then PENDING for retry)
        verify(outboxEventRepository, times(2)).save(same(pending));
        verify(outboxEventRepository, never()).delete(any());

        // Entity was reset to PENDING for retry (retryCount=1 < maxRetries=3)
        assertThat(pending.getStatus()).isEqualTo(OutboxEvent.Status.PENDING);
        assertThat(pending.getRetryCount()).isEqualTo(1);
        assertThat(pending.getLastError()).contains("Kafka down");
    }

    // ---------------------------------------------------------------
    // 4. Max retries reached → save(PUBLISHING) + save(FAILED), stays FAILED
    // ---------------------------------------------------------------
    @Test
    void maxRetriesReached_staysFailed() {
        mockTransaction();
        outboxProcessor.setMaxRetries(3);
        OutboxEvent pending = createPendingEvent();
        pending.setRetryCount(2); // One more attempt = maxRetries (3)
        when(outboxEventRepository.findTop10ByStatusOrderByCreatedAtAsc(OutboxEvent.Status.PENDING))
                .thenReturn(List.of(pending));
        doThrow(new RuntimeException("Kafka still down"))
                .when(messagePublisher).publish(anyString(), any());

        outboxProcessor.processOutbox();

        // save called twice (PUBLISHING then FAILED)
        verify(outboxEventRepository, times(2)).save(same(pending));
        verify(outboxEventRepository, never()).delete(any());

        // Entity stays FAILED (max retries reached)
        assertThat(pending.getStatus()).isEqualTo(OutboxEvent.Status.FAILED);
        assertThat(pending.getRetryCount()).isEqualTo(3);
    }

    // ---------------------------------------------------------------
    // 5. cleanupEvents — no stale rows → no-op
    // ---------------------------------------------------------------
    @Test
    void cleanupEvents_noStaleEvents_doesNothing() {
        mockTransaction();
        when(outboxEventRepository.findByStatusAndCreatedAtBefore(eq(OutboxEvent.Status.PUBLISHED), any(Instant.class)))
                .thenReturn(List.of());
        when(outboxEventRepository.findByStatusAndCreatedAtBefore(eq(OutboxEvent.Status.FAILED), any(Instant.class)))
                .thenReturn(List.of());
        when(outboxEventRepository.findByStatusAndCreatedAtBefore(eq(OutboxEvent.Status.PUBLISHING), any(Instant.class)))
                .thenReturn(List.of());

        outboxProcessor.cleanupEvents();

        verify(outboxEventRepository, never()).deleteAllInBatch(anyList());
        verify(outboxEventRepository, never()).saveAll(anyList());
    }

    // ---------------------------------------------------------------
    // 6. cleanupEvents — deletes stale PUBLISHED rows
    // ---------------------------------------------------------------
    @Test
    void cleanupEvents_deletesStalePublished() {
        mockTransaction();
        OutboxEvent stalePublished = createEvent(OutboxEvent.Status.PUBLISHED);
        when(outboxEventRepository.findByStatusAndCreatedAtBefore(eq(OutboxEvent.Status.PUBLISHED), any(Instant.class)))
                .thenReturn(List.of(stalePublished));
        when(outboxEventRepository.findByStatusAndCreatedAtBefore(eq(OutboxEvent.Status.FAILED), any(Instant.class)))
                .thenReturn(List.of());
        when(outboxEventRepository.findByStatusAndCreatedAtBefore(eq(OutboxEvent.Status.PUBLISHING), any(Instant.class)))
                .thenReturn(List.of());

        outboxProcessor.cleanupEvents();

        verify(outboxEventRepository).deleteAllInBatch(List.of(stalePublished));
        verify(outboxEventRepository, never()).saveAll(anyList());
    }

    // ---------------------------------------------------------------
    // 7. cleanupEvents — deletes stale FAILED rows
    // ---------------------------------------------------------------
    @Test
    void cleanupEvents_deletesStaleFailed() {
        mockTransaction();
        OutboxEvent staleFailed = createEvent(OutboxEvent.Status.FAILED);
        when(outboxEventRepository.findByStatusAndCreatedAtBefore(eq(OutboxEvent.Status.PUBLISHED), any(Instant.class)))
                .thenReturn(List.of());
        when(outboxEventRepository.findByStatusAndCreatedAtBefore(eq(OutboxEvent.Status.FAILED), any(Instant.class)))
                .thenReturn(List.of(staleFailed));
        when(outboxEventRepository.findByStatusAndCreatedAtBefore(eq(OutboxEvent.Status.PUBLISHING), any(Instant.class)))
                .thenReturn(List.of());

        outboxProcessor.cleanupEvents();

        verify(outboxEventRepository).deleteAllInBatch(List.of(staleFailed));
        verify(outboxEventRepository, never()).saveAll(anyList());
    }

    // ---------------------------------------------------------------
    // 8. cleanupEvents — recovers stuck PUBLISHING zombies to PENDING
    // ---------------------------------------------------------------
    @Test
    void cleanupEvents_recoversStuckPublishing() {
        mockTransaction();
        OutboxEvent stuckPublishing = createEvent(OutboxEvent.Status.PUBLISHING);
        when(outboxEventRepository.findByStatusAndCreatedAtBefore(eq(OutboxEvent.Status.PUBLISHED), any(Instant.class)))
                .thenReturn(List.of());
        when(outboxEventRepository.findByStatusAndCreatedAtBefore(eq(OutboxEvent.Status.FAILED), any(Instant.class)))
                .thenReturn(List.of());
        when(outboxEventRepository.findByStatusAndCreatedAtBefore(eq(OutboxEvent.Status.PUBLISHING), any(Instant.class)))
                .thenReturn(List.of(stuckPublishing));

        outboxProcessor.cleanupEvents();

        verify(outboxEventRepository, never()).deleteAllInBatch(anyList());
        verify(outboxEventRepository).saveAll(List.of(stuckPublishing));
        assertThat(stuckPublishing.getStatus()).isEqualTo(OutboxEvent.Status.PENDING);
    }
}
