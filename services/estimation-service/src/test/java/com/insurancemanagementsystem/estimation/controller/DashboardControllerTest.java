package com.insurancemanagementsystem.estimation.controller;

import com.insurancemanagementsystem.common.web.exception.GlobalExceptionHandler;
import com.insurancemanagementsystem.estimation.dto.DashboardSummary;
import com.insurancemanagementsystem.estimation.service.DashboardService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.client.RestTestClient;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@WebMvcTest(controllers = DashboardController.class)
@Import(GlobalExceptionHandler.class)
class DashboardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private RestTestClient restTestClient;

    @MockitoBean
    private DashboardService dashboardService;

    @BeforeEach
    void setUp() {
        restTestClient = RestTestClient.bindTo(mockMvc).build();
    }

    private DashboardSummary createSampleSummary() {
        return new DashboardSummary(
                new DashboardSummary.Kpis(
                        5L,
                        3L,
                        new BigDecimal("150000.00"),
                        "150.000,00 \u20BA",
                        2L,
                        Map.of("ACTIVE", 5L, "WAITING_APPROVAL", 3L)
                ),
                List.of(
                        new DashboardSummary.PendingAction(
                                UUID.randomUUID(),
                                "Ahmet Yılmaz",
                                "Vehicle",
                                new BigDecimal("5000.00"),
                                "5.000,00 \u20BA",
                                3L,
                                Instant.now().plus(2, ChronoUnit.MINUTES),
                                "WAITING_APPROVAL"
                        )
                ),
                List.of(
                        new DashboardSummary.RecentActivity(
                                "ESTIMATION_CREATED",
                                UUID.randomUUID(),
                                Instant.now(),
                                "ESTIMATION_CREATED"
                        )
                )
        );
    }

    // ---------------------------------------------------------------
    // 1. GET /api/dashboard/summary — returns HTTP 200
    // ---------------------------------------------------------------
    @Test
    void getSummary_shouldReturn200() {
        DashboardSummary summary = createSampleSummary();
        given(dashboardService.getDashboardSummary()).willReturn(summary);

        restTestClient.get()
                .uri("/api/dashboard/summary")
                .exchange()
                .expectStatus()
                .isOk();

        verify(dashboardService).getDashboardSummary();
    }

    // ---------------------------------------------------------------
    // 2. GET /api/dashboard/summary — response has ApiResponse wrapper
    // ---------------------------------------------------------------
    @Test
    void getSummary_shouldHaveApiResponseWrapper() {
        DashboardSummary summary = createSampleSummary();
        given(dashboardService.getDashboardSummary()).willReturn(summary);

        restTestClient.get()
                .uri("/api/dashboard/summary")
                .exchange()
                .expectBody()
                .jsonPath("$.success")
                .isEqualTo(true)
                .jsonPath("$.message")
                .isEqualTo("Operation successful")
                .jsonPath("$.data")
                .isNotEmpty()
                .jsonPath("$.timestamp")
                .isNotEmpty();
    }

    // ---------------------------------------------------------------
    // 3. GET /api/dashboard/summary — response data contains sections
    // ---------------------------------------------------------------
    @Test
    void getSummary_shouldReturnKpisPendingActionsRecentActivity() {
        DashboardSummary summary = createSampleSummary();
        given(dashboardService.getDashboardSummary()).willReturn(summary);

        restTestClient.get()
                .uri("/api/dashboard/summary")
                .exchange()
                .expectBody()
                .jsonPath("$.data.kpis.activePolicies")
                .isEqualTo(5)
                .jsonPath("$.data.kpis.pendingApprovals")
                .isEqualTo(3)
                .jsonPath("$.data.kpis.totalPremiumVolume")
                .isEqualTo(150000.00)
                .jsonPath("$.data.kpis.totalPremiumVolumeFormatted")
                .isEqualTo("150.000,00 \u20BA")
                .jsonPath("$.data.kpis.newCustomers30d")
                .isEqualTo(2)
                .jsonPath("$.data.pendingActions[0].customerName")
                .isEqualTo("Ahmet Yılmaz")
                .jsonPath("$.data.pendingActions[0].insuranceType")
                .isEqualTo("Vehicle")
                .jsonPath("$.data.pendingActions[0].status")
                .isEqualTo("WAITING_APPROVAL")
                .jsonPath("$.data.recentActivity[0].eventType")
                .isEqualTo("ESTIMATION_CREATED");
    }
}
