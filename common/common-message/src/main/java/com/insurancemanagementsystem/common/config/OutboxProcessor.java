package com.insurancemanagementsystem.common.config;

import com.insurancemanagementsystem.common.entity.OutboxEvent;
import com.insurancemanagementsystem.common.repository.OutboxEventRepository;
import com.insurancemanagementsystem.common.messaging.MessagePublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxProcessor {

	private final OutboxEventRepository outboxEventRepository;

	private final MessagePublisher messagePublisher;

	private final TransactionTemplate transactionTemplate;

	private int maxRetries = 3;

	private int failedTtlMinutes = 60;

	void setMaxRetries(int maxRetries) {
		this.maxRetries = maxRetries;
	}

	void setFailedTtlMinutes(int failedTtlMinutes) {
		this.failedTtlMinutes = failedTtlMinutes;
	}

	/**
	 * Process pending outbox events within a single transaction. Called by
	 * {@link OutboxRelay} on its scheduled thread.
	 */
	public void processOutbox() {
		transactionTemplate.executeWithoutResult(status -> {
			List<OutboxEvent> pendingEvents = outboxEventRepository
				.findTop10ByStatusOrderByCreatedAtAsc(OutboxEvent.Status.PENDING.name());

			if (pendingEvents.isEmpty()) {
				return;
			}

			log.debug("Processing {} outbox events", pendingEvents.size());

			for (OutboxEvent event : pendingEvents) {
				try {
					// Mark as PUBLISHING
					event.setStatus(OutboxEvent.Status.PUBLISHING);
					outboxEventRepository.save(event);

					// Publish to Kafka
					messagePublisher.publish(event.getTopic(), event.getPayload());

					// Mark as PUBLISHED (will be cleaned up later, not immediately
					// deleted)
					event.setStatus(OutboxEvent.Status.PUBLISHED);
					outboxEventRepository.save(event);
					log.debug("Published outbox event id={} to topic={}", event.getId(), event.getTopic());
				}
				catch (Exception e) {
					log.error("Failed to publish outbox event id={} to topic={}: {}", event.getId(), event.getTopic(),
							e.getMessage());
					event.setStatus(OutboxEvent.Status.FAILED);
					event.setRetryCount(event.getRetryCount() + 1);
					event.setLastError(e.getMessage());
					if (event.getRetryCount() >= maxRetries) {
						log.warn("Outbox event id={} reached max retries ({}). Giving up.", event.getId(), maxRetries);
					}
					else {
						event.setStatus(OutboxEvent.Status.PENDING);
					}
					outboxEventRepository.save(event);
				}
			}
		});
	}

	/**
	 * Clean up successfully published events and old FAILED events. Also recovers
	 * PUBLISHING zombies stuck for more than 5 minutes.
	 */
	public void cleanupEvents() {
		transactionTemplate.executeWithoutResult(status -> {
			Instant publishCutoff = Instant.now().minus(5, ChronoUnit.MINUTES);
			List<OutboxEvent> stalePublished = outboxEventRepository
				.findByStatusAndCreatedAtBefore(OutboxEvent.Status.PUBLISHED, publishCutoff);
			if (!stalePublished.isEmpty()) {
				outboxEventRepository.deleteAllInBatch(stalePublished);
				log.info("Cleaned up {} stale PUBLISHED outbox events", stalePublished.size());
			}

			Instant failedCutoff = Instant.now().minus(failedTtlMinutes, ChronoUnit.MINUTES);
			List<OutboxEvent> staleFailed = outboxEventRepository
				.findByStatusAndCreatedAtBefore(OutboxEvent.Status.FAILED, failedCutoff);
			if (!staleFailed.isEmpty()) {
				outboxEventRepository.deleteAllInBatch(staleFailed);
				log.info("Cleaned up {} stale FAILED outbox events", staleFailed.size());
			}

			// Recover PUBLISHING zombies (stuck for > 5 minutes)
			Instant publishingCutoff = Instant.now().minus(5, ChronoUnit.MINUTES);
			List<OutboxEvent> stalePublishing = outboxEventRepository
				.findByStatusAndCreatedAtBefore(OutboxEvent.Status.PUBLISHING, publishingCutoff);
			if (!stalePublishing.isEmpty()) {
				for (OutboxEvent event : stalePublishing) {
					event.setStatus(OutboxEvent.Status.PENDING);
					log.warn("Recovering stuck PUBLISHING outbox event id={}", event.getId());
				}
				outboxEventRepository.saveAll(stalePublishing);
				log.info("Recovered {} stuck PUBLISHING outbox events", stalePublishing.size());
			}
		});
	}

}
