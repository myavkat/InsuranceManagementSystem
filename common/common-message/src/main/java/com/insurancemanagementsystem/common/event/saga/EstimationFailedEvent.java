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
public class EstimationFailedEvent extends BaseEvent {
    private UUID originalSagaId;
    private String reason;
    private String failedStep;

    @Override
    public String getEventType() {
        return EventConstants.ESTIMATION_FAILED;
    }
}
