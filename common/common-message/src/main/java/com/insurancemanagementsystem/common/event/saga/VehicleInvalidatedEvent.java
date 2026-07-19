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
public class VehicleInvalidatedEvent extends BaseEvent {

	private UUID vehicleId;

	private String reason;

	@Override
	public String getEventType() {
		return EventConstants.VEHICLE_INVALIDATED;
	}

}
