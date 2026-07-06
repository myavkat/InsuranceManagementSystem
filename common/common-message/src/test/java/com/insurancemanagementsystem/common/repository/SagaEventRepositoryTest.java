package com.insurancemanagementsystem.common.repository;

import com.insurancemanagementsystem.common.entity.SagaEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = SagaEventRepositoryTest.Config.class)
@Transactional
class SagaEventRepositoryTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }

    @Configuration
    @EnableAutoConfiguration
    @EnableJpaRepositories(basePackageClasses = SagaEventRepository.class)
    @EntityScan(basePackageClasses = SagaEvent.class)
    static class Config {
    }

    @Autowired
    private SagaEventRepository repository;

    /**
     * Regression test: calling {@code tryInsertDedup} without an active
     * transaction must throw because {@code insertDedupMarker} is annotated
     * {@code @Transactional(propagation = MANDATORY)}.  This guarantees that
     * dedup-marking and the subsequent side effects (e.g. outbox save) always
     * share a single transaction — a silent violation would mean the two
     * writes can commit independently and diverge.
     */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void tryInsertDedup_withoutTransaction_shouldThrow() {
        UUID sagaId = UUID.randomUUID();
        assertThatThrownBy(() -> repository.tryInsertDedup(sagaId, "EstimationRequested"))
                .isInstanceOf(IllegalTransactionStateException.class)
                .hasMessageContaining("existing transaction");
    }

    /**
     * Happy-path test: the first call for a given (sagaId, eventType) pair
     * returns {@code false} (new event), and the second call returns
     * {@code true} (duplicate).  The native {@code INSERT … ON CONFLICT DO
     * NOTHING} handles the atomic dedup without creating a managed JPA entity,
     * so the persistence context stays clean.
     */
    @Test
    void tryInsertDedup_firstCallReturnsFalse_secondCallReturnsTrue() {
        UUID sagaId = UUID.randomUUID();

        assertThat(repository.tryInsertDedup(sagaId, "EstimationRequested")).isFalse();
        assertThat(repository.tryInsertDedup(sagaId, "EstimationRequested")).isTrue();
    }
}
