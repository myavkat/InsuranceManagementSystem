package com.insurancemanagementsystem.common.event.saga;

import com.insurancemanagementsystem.common.event.BaseEvent;
import com.insurancemanagementsystem.common.event.EventConstants;
import lombok.*;

import java.util.UUID;

@EqualsAndHashCode(callSuper = true)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RealEstateValidatedEvent extends BaseEvent {
    private UUID realEstateId;
    private String address;
    private Integer cityId;

    @Override
    public String getEventType() {
        return EventConstants.REAL_ESTATE_VALIDATED;
    }
}
