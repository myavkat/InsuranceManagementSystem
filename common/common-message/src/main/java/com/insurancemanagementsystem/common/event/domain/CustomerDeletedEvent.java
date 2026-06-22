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
public class CustomerDeletedEvent extends BaseEvent {
    private UUID customerId;
    private String nationalId;

    @Override
    public String getEventType() {
        return EventConstants.CUSTOMER_DELETED;
    }
}
