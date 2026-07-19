package com.insurancemanagementsystem.vehicle.config;

import com.insurancemanagementsystem.common.event.EventConstants;
import com.insurancemanagementsystem.common.event.EventEnvelope;
import com.insurancemanagementsystem.common.event.domain.VehicleCreatedEvent;
import com.insurancemanagementsystem.common.event.domain.VehicleDeletedEvent;
import com.insurancemanagementsystem.common.event.domain.VehicleUpdatedEvent;
import com.insurancemanagementsystem.common.messaging.MessagePublisher;
import com.insurancemanagementsystem.vehicle.entity.Vehicle;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class VehicleEventPublisher {

	private final MessagePublisher messagePublisher;

	public void publishVehicleCreated(Vehicle vehicle) {
		VehicleCreatedEvent event = VehicleCreatedEvent.builder()
			.vehicleId(vehicle.getId())
			.plate(vehicle.getPlate())
			.customerId(vehicle.getCustomerId())
			.carBrandId(vehicle.getCarBrandId())
			.build();

		EventEnvelope envelope = event.toEnvelope(null, UUID.randomUUID());
		messagePublisher.publish(EventConstants.VEHICLE_EVENTS, envelope);
		log.info("Published VehicleCreated event for vehicle id: {}", vehicle.getId());
	}

	public void publishVehicleUpdated(Vehicle vehicle) {
		VehicleUpdatedEvent event = VehicleUpdatedEvent.builder()
			.vehicleId(vehicle.getId())
			.plate(vehicle.getPlate())
			.customerId(vehicle.getCustomerId())
			.carBrandId(vehicle.getCarBrandId())
			.build();

		EventEnvelope envelope = event.toEnvelope(null, UUID.randomUUID());
		messagePublisher.publish(EventConstants.VEHICLE_EVENTS, envelope);
		log.info("Published VehicleUpdated event for vehicle id: {}", vehicle.getId());
	}

	public void publishVehicleDeleted(Vehicle vehicle) {
		VehicleDeletedEvent event = VehicleDeletedEvent.builder()
			.vehicleId(vehicle.getId())
			.plate(vehicle.getPlate())
			.build();

		EventEnvelope envelope = event.toEnvelope(null, UUID.randomUUID());
		messagePublisher.publish(EventConstants.VEHICLE_EVENTS, envelope);
		log.info("Published VehicleDeleted event for vehicle id: {}", vehicle.getId());
	}

}
