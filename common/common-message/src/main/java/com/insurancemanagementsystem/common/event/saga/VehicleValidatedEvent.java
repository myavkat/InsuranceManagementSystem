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
public class VehicleValidatedEvent extends BaseEvent {

	private UUID vehicleId;

	private String plate;

	private String brand;

	private String model;

	@Override
	public String getEventType() {
		return EventConstants.VEHICLE_VALIDATED;
	}

}
