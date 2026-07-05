package com.insurancemanagementsystem.insurance.config;

import com.insurancemanagementsystem.common.messaging.MessagePublisher;
import com.insurancemanagementsystem.insurance.entity.OutboxEvent;
import com.insurancemanagementsystem.insurance.repository.OutboxEventRepository;
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

    @Test
    void pendingEvent_publishedSuccessfully() {
        mockTransaction();
        OutboxEvent pending = createPendingEvent();
        when(outboxEventRepository.findTop10ByStatusOrderByCreatedAtAsc(OutboxEvent.Status.PENDING))
                .thenReturn(List.of(pending));

        outboxProcessor.processOutbox();

        verify(outboxEventRepository, times(2)).save(same(pending));
        verify(messagePublisher).publish(pending.getTopic(), pending.getPayload());
        verify(outboxEventRepository, never()).delete(any());

        assertThat(pending.getStatus()).isEqualTo(OutboxEvent.Status.PUBLISHED);
    }

    @Test
    void publishFails_savesTwiceAndRetries() {
        mockTransaction();
        OutboxEvent pending = createPendingEvent();
        when(outboxEventRepository.findTop10ByStatusOrderByCreatedAtAsc(OutboxEvent.Status.PENDING))
                .thenReturn(List.of(pending));
        doThrow(new RuntimeException("Kafka down"))
                .when(messagePublisher).publish(anyString(), any());

        outboxProcessor.processOutbox();

        verify(outboxEventRepository, times(2)).save(same(pending));
        verify(outboxEventRepository, never()).delete(any());

        assertThat(pending.getStatus()).isEqualTo(OutboxEvent.Status.PENDING);
        assertThat(pending.getRetryCount()).isEqualTo(1);
        assertThat(pending.getLastError()).contains("Kafka down");
    }

    @Test
    void maxRetriesReached_staysFailed() {
        mockTransaction();
        outboxProcessor.setMaxRetries(3);
        OutboxEvent pending = createPendingEvent();
        pending.setRetryCount(2);
        when(outboxEventRepository.findTop10ByStatusOrderByCreatedAtAsc(OutboxEvent.Status.PENDING))
                .thenReturn(List.of(pending));
        doThrow(new RuntimeException("Kafka still down"))
                .when(messagePublisher).publish(anyString(), any());

        outboxProcessor.processOutbox();

        verify(outboxEventRepository, times(2)).save(same(pending));
        verify(outboxEventRepository, never()).delete(any());

        assertThat(pending.getStatus()).isEqualTo(OutboxEvent.Status.FAILED);
        assertThat(pending.getRetryCount()).isEqualTo(3);
    }

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
