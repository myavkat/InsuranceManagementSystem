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
public class InsuranceUpdatedEvent extends BaseEvent {

	private UUID insuranceId;

	private Integer typeId;

	private String name;

	@Override
	public String getEventType() {
		return EventConstants.INSURANCE_UPDATED;
	}

}
