package com.insurancemanagementsystem.estimation.service;

import com.insurancemanagementsystem.common.entity.SagaEvent;
import com.insurancemanagementsystem.common.repository.SagaEventRepository;
import com.insurancemanagementsystem.estimation.client.CustomerServiceClient;
import com.insurancemanagementsystem.estimation.client.InsuranceServiceClient;
import com.insurancemanagementsystem.estimation.dto.DashboardSummary;
import com.insurancemanagementsystem.estimation.entity.Estimation;
import com.insurancemanagementsystem.estimation.repository.EstimationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class DashboardService {

    private static final Map<Integer, String> INSURANCE_TYPE_NAMES = Map.of(
        1, "Vehicle",
        2, "Real Estate",
        3, "Health",
        4, "Life"
    );

    private final EstimationRepository estimationRepository;
    private final SagaEventRepository sagaEventRepository;
    private final CustomerServiceClient customerServiceClient;
    private final InsuranceServiceClient insuranceServiceClient;

    @Transactional(readOnly = true)
    public DashboardSummary getDashboardSummary() {
        return new DashboardSummary(
            buildKpis(),
            buildPendingActions(),
            buildRecentActivity()
        );
    }

    @Transactional(readOnly = true)
    public DashboardSummary.Kpis buildKpis() {
        long activePolicies = estimationRepository.countByStatus(Estimation.Status.ACTIVE);
        long pendingApprovals = estimationRepository.countByStatus(Estimation.Status.WAITING_APPROVAL);
        BigDecimal totalPremiumVolume = estimationRepository.sumPremiumByActiveStatus();

        NumberFormat trFormat = NumberFormat.getNumberInstance(new Locale("tr", "TR"));
        trFormat.setMinimumFractionDigits(2);
        String totalPremiumVolumeFormatted = trFormat.format(totalPremiumVolume) + " \u20BA";

        Instant thirtyDaysAgo = Instant.now().minus(30, ChronoUnit.DAYS);
        long newCustomers30d = estimationRepository.countDistinctCustomerIdsSince(thirtyDaysAgo);

        Map<String, Long> estimationsByStatus = new LinkedHashMap<>();
        List<Object[]> statusCounts = estimationRepository.countGroupedByStatus();
        for (Object[] row : statusCounts) {
            estimationsByStatus.put(((Estimation.Status) row[0]).name(), (Long) row[1]);
        }

        return new DashboardSummary.Kpis(
            activePolicies,
            pendingApprovals,
            totalPremiumVolume,
            totalPremiumVolumeFormatted,
            newCustomers30d,
            estimationsByStatus
        );
    }

    @Transactional(readOnly = true)
    public List<DashboardSummary.PendingAction> buildPendingActions() {
        List<Estimation> pending = estimationRepository.findByStatusIn(
            List.of(Estimation.Status.WAITING_APPROVAL, Estimation.Status.PAYMENT_WAITING),
            Sort.by(Sort.Direction.ASC, "createdAt")
        );

        NumberFormat trFormat = NumberFormat.getNumberInstance(new Locale("tr", "TR"));
        trFormat.setMinimumFractionDigits(2);

        return pending.stream().map(est -> {
            String customerName = "Unknown";
            try {
                String name = customerServiceClient.getCustomerName(est.getCustomerId());
                if (name != null) {
                    customerName = name;
                }
            } catch (Exception e) {
                log.warn("Failed to fetch customer name for customerId={}: {}", est.getCustomerId(), e.getMessage());
            }

            String insuranceType = "Unknown";
            if (est.getInsuranceId() != null) {
                try {
                    InsuranceServiceClient.InsuranceInfo info = insuranceServiceClient.getInsurance(est.getInsuranceId());
                    if (info != null) {
                        insuranceType = info.typeName() != null ? info.typeName()
                            : INSURANCE_TYPE_NAMES.getOrDefault(info.typeId(), "Unknown");
                    }
                } catch (Exception e) {
                    log.warn("Failed to fetch insurance info for insuranceId={}: {}", est.getInsuranceId(), e.getMessage());
                }
            }

            long ageInMinutes = Duration.between(est.getCreatedAt(), Instant.now()).toMinutes();
            Instant timeoutDeadline = est.getCreatedAt().plus(5, ChronoUnit.MINUTES);

            String premiumFormatted = est.getPremium() != null
                ? trFormat.format(est.getPremium()) + " \u20BA"
                : null;

            return new DashboardSummary.PendingAction(
                est.getSagaId(),
                customerName,
                insuranceType,
                est.getPremium(),
                premiumFormatted,
                ageInMinutes,
                timeoutDeadline,
                est.getStatus().name()
            );
        }).toList();
    }

    @Transactional(readOnly = true)
    public List<DashboardSummary.RecentActivity> buildRecentActivity() {
        List<SagaEvent> recentEvents = sagaEventRepository.findAll(
            PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "receivedAt"))
        ).getContent();

        return recentEvents.stream().map(event -> new DashboardSummary.RecentActivity(
            event.getEventType(),
            event.getSagaId(),
            event.getReceivedAt(),
            event.getEventType()
        )).toList();
    }
}
