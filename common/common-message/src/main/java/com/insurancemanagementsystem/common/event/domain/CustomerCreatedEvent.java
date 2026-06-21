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
public class CustomerCreatedEvent extends BaseEvent {
    private UUID customerId;
    private String nationalId;
    private String email;

    @Override
    public String getEventType() {
        return EventConstants.CUSTOMER_CREATED;
    }
}
