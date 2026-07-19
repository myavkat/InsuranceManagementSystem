package com.insurancemanagementsystem.common.event.saga;

import com.insurancemanagementsystem.common.event.BaseEvent;
import com.insurancemanagementsystem.common.event.EventConstants;
import lombok.*;

@EqualsAndHashCode(callSuper = true)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CalculationFailedEvent extends BaseEvent {

	private String reason;

	@Override
	public String getEventType() {
		return EventConstants.CALCULATION_FAILED;
	}

}
