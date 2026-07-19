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
public class VehicleCreatedEvent extends BaseEvent {

	private UUID vehicleId;

	private String plate;

	private UUID customerId;

	private Integer carBrandId;

	@Override
	public String getEventType() {
		return EventConstants.VEHICLE_CREATED;
	}

}
