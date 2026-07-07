package com.insurancemanagementsystem.common.event.saga;

import com.insurancemanagementsystem.common.event.BaseEvent;
import com.insurancemanagementsystem.common.event.EventConstants;
import lombok.*;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@EqualsAndHashCode(callSuper = true)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PremiumCalculatedEvent extends BaseEvent {
    private BigDecimal premium;
    private Map<String, BigDecimal> breakdown;
    private Integer insuranceTypeId;
    private UUID customerId;
    private UUID vehicleId;

    @Override
    public String getEventType() {
        return EventConstants.PREMIUM_CALCULATED;
    }
}
