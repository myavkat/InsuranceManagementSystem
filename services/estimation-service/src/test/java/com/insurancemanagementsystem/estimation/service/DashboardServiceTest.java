package com.insurancemanagementsystem.estimation.service;

import com.insurancemanagementsystem.common.entity.SagaEvent;
import com.insurancemanagementsystem.common.repository.SagaEventRepository;
import com.insurancemanagementsystem.estimation.client.CustomerServiceClient;
import com.insurancemanagementsystem.estimation.client.InsuranceServiceClient;
import com.insurancemanagementsystem.estimation.dto.DashboardSummary;
import com.insurancemanagementsystem.estimation.entity.Estimation;
import com.insurancemanagementsystem.estimation.repository.EstimationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock
    private EstimationRepository estimationRepository;

    @Mock
    private SagaEventRepository sagaEventRepository;

    @Mock
    private CustomerServiceClient customerServiceClient;

    @Mock
    private InsuranceServiceClient insuranceServiceClient;

    @InjectMocks
    private DashboardService dashboardService;

    @Captor
    private ArgumentCaptor<List<Estimation.Status>> statusesCaptor;

    @Captor
    private ArgumentCaptor<Instant> instantCaptor;

    // ---------------------------------------------------------------
    // Helper methods
    // ---------------------------------------------------------------

    private Estimation createEstimation(Estimation.Status status, Instant createdAt) {
        return Estimation.builder()
                .id(UUID.randomUUID())
                .sagaId(UUID.randomUUID())
                .customerId(UUID.randomUUID())
                .status(status)
                .createdAt(createdAt)
                .premium(new BigDecimal("5000.00"))
                .build();
    }

    private void mockAllKpisDefaults() {
        lenient().when(estimationRepository.countByStatus(any(Estimation.Status.class))).thenReturn(0L);
        lenient().when(estimationRepository.sumPremiumByActiveStatus()).thenReturn(BigDecimal.ZERO);
        lenient().when(estimationRepository.countDistinctCustomerIdsSince(any(Instant.class))).thenReturn(0L);
        lenient().when(estimationRepository.countGroupedByStatus()).thenReturn(List.of());
    }

    private void mockAllPendingActionsDefaults() {
        lenient().when(estimationRepository.findByStatusIn(anyList(), any(Sort.class))).thenReturn(List.of());
        lenient().when(customerServiceClient.getCustomerName(any(UUID.class))).thenReturn("Test Customer");
    }

    private void mockAllRecentActivityDefaults() {
        lenient().when(sagaEventRepository.findAll(any(Pageable.class))).thenReturn(Page.empty());
    }

    // ---------------------------------------------------------------
    // 1. KPIs: getDashboardSummary returns correct counts
    // ---------------------------------------------------------------
    @Test
    void getDashboardSummary_shouldReturnKpisWithCorrectCounts() {
        // Arrange
        mockAllKpisDefaults();
        mockAllPendingActionsDefaults();
        mockAllRecentActivityDefaults();

        when(estimationRepository.countByStatus(Estimation.Status.ACTIVE)).thenReturn(5L);
        when(estimationRepository.countByStatus(Estimation.Status.WAITING_APPROVAL)).thenReturn(3L);
        when(estimationRepository.sumPremiumByActiveStatus()).thenReturn(new BigDecimal("150000.00"));

        // Act
        DashboardSummary summary = dashboardService.getDashboardSummary();

        // Assert
        assertThat(summary).isNotNull();
        assertThat(summary.kpis().activePolicies()).isEqualTo(5L);
        assertThat(summary.kpis().pendingApprovals()).isEqualTo(3L);
        assertThat(summary.kpis().totalPremiumVolume()).isEqualByComparingTo(new BigDecimal("150000.00"));
    }

    // ---------------------------------------------------------------
    // 2. Pending Actions: sorted by timeout deadline ascending
    // ---------------------------------------------------------------
    @Test
    void buildPendingActions_shouldSortByTimeoutAscending() {
        // Arrange
        Instant now = Instant.now();
        Estimation older = createEstimation(Estimation.Status.WAITING_APPROVAL, now.minus(4, ChronoUnit.MINUTES));
        Estimation newer = createEstimation(Estimation.Status.WAITING_APPROVAL, now.minus(1, ChronoUnit.MINUTES));

        when(estimationRepository.findByStatusIn(anyList(), any(Sort.class))).thenReturn(List.of(older, newer));
        when(customerServiceClient.getCustomerName(any(UUID.class))).thenReturn("Test Customer");

        // Act
        List<DashboardSummary.PendingAction> actions = dashboardService.buildPendingActions();

        // Assert
        assertThat(actions).hasSize(2);
        // older (created earlier, closer to timeout) should come first
        assertThat(actions.get(0).sagaId()).isEqualTo(older.getSagaId());
        assertThat(actions.get(1).sagaId()).isEqualTo(newer.getSagaId());
    }

    // ---------------------------------------------------------------
    // 3. Pending Actions: exclude ACTIVE and non-pending statuses
    // ---------------------------------------------------------------
    @Test
    void buildPendingActions_shouldExcludeActiveAndFailedStatuses() {
        // Arrange
        when(estimationRepository.findByStatusIn(anyList(), any(Sort.class))).thenReturn(List.of());

        // Act
        dashboardService.buildPendingActions();

        // Assert
        verify(estimationRepository).findByStatusIn(statusesCaptor.capture(), any(Sort.class));
        List<Estimation.Status> capturedStatuses = statusesCaptor.getValue();

        assertThat(capturedStatuses)
                .containsExactly(Estimation.Status.WAITING_APPROVAL, Estimation.Status.PAYMENT_WAITING)
                .doesNotContain(Estimation.Status.ACTIVE, Estimation.Status.COMPLETED, Estimation.Status.REJECTED);
    }

    // ---------------------------------------------------------------
    // 4. Pending Actions: include WAITING_APPROVAL and PAYMENT_WAITING
    // ---------------------------------------------------------------
    @Test
    void buildPendingActions_shouldIncludeWaitingApprovalAndPaymentWaiting() {
        // Arrange
        Estimation waEst = createEstimation(Estimation.Status.WAITING_APPROVAL, Instant.now());
        Estimation pwEst = createEstimation(Estimation.Status.PAYMENT_WAITING, Instant.now());

        when(estimationRepository.findByStatusIn(anyList(), any(Sort.class))).thenReturn(List.of(waEst, pwEst));
        when(customerServiceClient.getCustomerName(any(UUID.class))).thenReturn("Test Customer");

        // Act
        List<DashboardSummary.PendingAction> actions = dashboardService.buildPendingActions();

        // Assert
        assertThat(actions).hasSize(2);
        assertThat(actions.get(0).status()).isEqualTo("WAITING_APPROVAL");
        assertThat(actions.get(1).status()).isEqualTo("PAYMENT_WAITING");
    }

    // ---------------------------------------------------------------
    // 5. Recent Activity: max 10 items, sorted by timestamp DESC
    // ---------------------------------------------------------------
    @Test
    void buildRecentActivity_shouldReturnMax10ItemsSortedByTimestampDesc() {
        // Arrange
        List<SagaEvent> events = IntStream.range(0, 5)
                .mapToObj(i -> SagaEvent.builder()
                        .sagaId(UUID.randomUUID())
                        .eventType("SAGA_EVENT_" + i)
                        .receivedAt(Instant.now().minus(i, ChronoUnit.MINUTES))
                        .build())
                .collect(Collectors.toList());

        Page<SagaEvent> eventPage = new PageImpl<>(events);
        when(sagaEventRepository.findAll(any(PageRequest.class))).thenReturn(eventPage);

        // Act
        List<DashboardSummary.RecentActivity> activities = dashboardService.buildRecentActivity();

        // Assert
        assertThat(activities).hasSize(5);
        // Verify sorted by receivedAt DESC — newest first
        for (int i = 0; i < activities.size() - 1; i++) {
            assertThat(activities.get(i).timestamp())
                    .isAfterOrEqualTo(activities.get(i + 1).timestamp());
        }
    }

    // ---------------------------------------------------------------
    // 6. Premium formatting with Turkish locale
    // ---------------------------------------------------------------
    @Test
    void getDashboardSummary_shouldFormatPremiumWithTurkishLocale() {
        // Arrange
        mockAllKpisDefaults();
        mockAllPendingActionsDefaults();
        mockAllRecentActivityDefaults();

        when(estimationRepository.sumPremiumByActiveStatus()).thenReturn(new BigDecimal("1500000.50"));
        when(estimationRepository.countByStatus(Estimation.Status.ACTIVE)).thenReturn(1L);

        // Act
        DashboardSummary summary = dashboardService.getDashboardSummary();

        // Assert
        String formatted = summary.kpis().totalPremiumVolumeFormatted();
        // Turkish locale: 1.500.000,50 ₺ (dot = thousands, comma = decimal)
        assertThat(formatted).contains("₺");
        assertThat(formatted).contains(","); // Turkish comma as decimal separator
    }

    // ---------------------------------------------------------------
    // 7. New customers count uses correct 30-day window
    // ---------------------------------------------------------------
    @Test
    void buildKpis_shouldUseCorrect30DayWindow() {
        // Arrange
        when(estimationRepository.countByStatus(any(Estimation.Status.class))).thenReturn(0L);
        when(estimationRepository.sumPremiumByActiveStatus()).thenReturn(BigDecimal.ZERO);
        when(estimationRepository.countGroupedByStatus()).thenReturn(List.of());

        // Act
        dashboardService.buildKpis();

        // Assert
        verify(estimationRepository).countDistinctCustomerIdsSince(instantCaptor.capture());
        Instant since = instantCaptor.getValue();
        Instant expected = Instant.now().minus(30, ChronoUnit.DAYS);
        assertThat(since).isCloseTo(expected, within(1, ChronoUnit.SECONDS));
    }
}
