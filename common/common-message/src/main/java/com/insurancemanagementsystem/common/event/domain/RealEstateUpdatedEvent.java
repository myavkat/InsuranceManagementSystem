package com.insurancemanagementsystem.common.event.domain;

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
public class RealEstateUpdatedEvent extends BaseEvent {
    private UUID realEstateId;

    @Override
    public String getEventType() {
        return EventConstants.REAL_ESTATE_UPDATED;
    }
}
