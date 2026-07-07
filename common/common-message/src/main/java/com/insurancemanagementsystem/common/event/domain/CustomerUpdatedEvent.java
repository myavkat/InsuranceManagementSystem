package com.insurancemanagementsystem.common.event.domain;

import com.insurancemanagementsystem.common.event.BaseEvent;
import com.insurancemanagementsystem.common.event.EventConstants;
import lombok.*;

import java.util.UUID;

@EqualsAndHashCode(callSuper = true)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerUpdatedEvent extends BaseEvent {
    private UUID customerId;
    private String nationalId;
    private String email;
    private String firstName;
    private String lastName;

    @Override
    public String getEventType() {
        return EventConstants.CUSTOMER_UPDATED;
    }
}
