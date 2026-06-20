package com.insurancemanagementsystem.common.event.saga;

import com.insurancemanagementsystem.common.event.BaseEvent;
import com.insurancemanagementsystem.common.event.EventConstants;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EstimationRequestedEvent extends BaseEvent {
    private UUID customerId;
    private UUID vehicleId;
    private UUID realEstateId;
    private Integer insuranceTypeId;
    private UUID companyId;

    @Override
    public String getEventType() {
        return EventConstants.ESTIMATION_REQUESTED;
    }
}
