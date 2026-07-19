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
public class RealEstateInvalidatedEvent extends BaseEvent {

	private UUID realEstateId;

	private String reason;

	@Override
	public String getEventType() {
		return EventConstants.REAL_ESTATE_INVALIDATED;
	}

}
