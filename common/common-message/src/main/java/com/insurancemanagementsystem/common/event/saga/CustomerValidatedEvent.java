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
public class CustomerValidatedEvent extends BaseEvent {
    private UUID customerId;
    private String firstName;
    private String lastName;

    @Override
    public String getEventType() {
        return EventConstants.CUSTOMER_VALIDATED;
    }
}
