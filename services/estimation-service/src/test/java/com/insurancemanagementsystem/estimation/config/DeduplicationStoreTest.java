package com.insurancemanagementsystem.estimation.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

class DeduplicationStoreTest {

    private DeduplicationStore store;

    @BeforeEach
    void setUp() {
        store = new DeduplicationStore();
    }

    // ---------------------------------------------------------------
    // 1. isDuplicate returns false for new key
    // ---------------------------------------------------------------
    @Test
    void isDuplicate_returnsFalseForNewKey() {
        assertThat(store.isDuplicate("saga-1", "CustomerValidated")).isFalse();
    }

    // ---------------------------------------------------------------
    // 2. markProcessed + isDuplicate returns true
    // ---------------------------------------------------------------
    @Test
    void markProcessed_thenIsDuplicate_returnsTrue() {
        store.markProcessed("saga-1", "CustomerValidated");

        assertThat(store.isDuplicate("saga-1", "CustomerValidated")).isTrue();
    }

    // ---------------------------------------------------------------
    // 3. Different event types with same sagaId are not duplicates
    // ---------------------------------------------------------------
    @Test
    void differentEventTypes_sameSagaId_areNotDuplicates() {
        store.markProcessed("saga-1", "CustomerValidated");

        assertThat(store.isDuplicate("saga-1", "VehicleValidated")).isFalse();
    }

    // ---------------------------------------------------------------
    // 4. Different sagaId with same event type are not duplicates
    // ---------------------------------------------------------------
    @Test
    void sameEventType_differentSagaIds_areNotDuplicates() {
        store.markProcessed("saga-1", "CustomerValidated");

        assertThat(store.isDuplicate("saga-2", "CustomerValidated")).isFalse();
    }

    // ---------------------------------------------------------------
    // 5. Cleanup removes expired entries
    // ---------------------------------------------------------------
    @Test
    void cleanup_removesExpiredEntries() throws Exception {
        // Mark a key as processed
        store.markProcessed("saga-1", "CustomerValidated");
        assertThat(store.isDuplicate("saga-1", "CustomerValidated")).isTrue();

        // Access the internal store via reflection to simulate expired timestamp
        java.lang.reflect.Field storeField = DeduplicationStore.class.getDeclaredField("store");
        storeField.setAccessible(true);
        @SuppressWarnings("unchecked")
        ConcurrentHashMap<String, Instant> internalStore =
                (ConcurrentHashMap<String, Instant>) storeField.get(store);

        // Set timestamp to 15 minutes ago (TTL is 10 min)
        internalStore.put("saga-1:CustomerValidated", Instant.now().minus(15, ChronoUnit.MINUTES));

        // Invoke cleanup via reflection
        java.lang.reflect.Method cleanupMethod = DeduplicationStore.class.getDeclaredMethod("cleanup");
        cleanupMethod.setAccessible(true);
        cleanupMethod.invoke(store);

        // Verify the expired entry was removed
        assertThat(store.isDuplicate("saga-1", "CustomerValidated")).isFalse();
    }

    // ---------------------------------------------------------------
    // 6. Cleanup keeps non-expired entries
    // ---------------------------------------------------------------
    @Test
    void cleanup_keepsNonExpiredEntries() throws Exception {
        // Mark a key as processed
        store.markProcessed("saga-1", "CustomerValidated");

        // Access internal store to set a recent timestamp
        java.lang.reflect.Field storeField = DeduplicationStore.class.getDeclaredField("store");
        storeField.setAccessible(true);
        @SuppressWarnings("unchecked")
        ConcurrentHashMap<String, Instant> internalStore =
                (ConcurrentHashMap<String, Instant>) storeField.get(store);

        // Set timestamp to 1 minute ago (within TTL of 10 min)
        internalStore.put("saga-1:CustomerValidated", Instant.now().minus(1, ChronoUnit.MINUTES));

        // Invoke cleanup via reflection
        java.lang.reflect.Method cleanupMethod = DeduplicationStore.class.getDeclaredMethod("cleanup");
        cleanupMethod.setAccessible(true);
        cleanupMethod.invoke(store);

        // Verify the entry is still there
        assertThat(store.isDuplicate("saga-1", "CustomerValidated")).isTrue();
    }
}
