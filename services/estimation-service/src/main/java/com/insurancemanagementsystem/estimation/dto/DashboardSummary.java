package com.insurancemanagementsystem.estimation.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record DashboardSummary(
    Kpis kpis,
    List<PendingAction> pendingActions,
    List<RecentActivity> recentActivity
) {
    public record Kpis(
        long activePolicies,
        long pendingApprovals,
        BigDecimal totalPremiumVolume,
        String totalPremiumVolumeFormatted,
        long newCustomers30d,
        Map<String, Long> estimationsByStatus
    ) {}

    public record PendingAction(
        UUID sagaId,
        String customerName,
        String insuranceType,
        BigDecimal premium,
        String premiumFormatted,
        long ageInMinutes,
        Instant timeoutDeadline,
        String status
    ) {}

    public record RecentActivity(
        String eventType,
        UUID sagaId,
        Instant timestamp,
        String description
    ) {}
}
