package com.insurancemanagementsystem.common.event.saga;

import com.insurancemanagementsystem.common.event.BaseEvent;
import com.insurancemanagementsystem.common.event.EventConstants;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PremiumCalculatedEvent extends BaseEvent {
    private BigDecimal premium;
    private Map<String, BigDecimal> breakdown;
    private Integer insuranceTypeId;
    private UUID companyId;
    private UUID customerId;
    private UUID vehicleId;

    @Override
    public String getEventType() {
        return EventConstants.PREMIUM_CALCULATED;
    }
}
