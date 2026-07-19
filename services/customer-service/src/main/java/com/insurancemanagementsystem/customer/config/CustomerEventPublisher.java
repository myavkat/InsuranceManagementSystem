package com.insurancemanagementsystem.customer.config;

import com.insurancemanagementsystem.common.event.EventConstants;
import com.insurancemanagementsystem.common.event.EventEnvelope;
import com.insurancemanagementsystem.common.event.domain.CustomerCreatedEvent;
import com.insurancemanagementsystem.common.event.domain.CustomerDeletedEvent;
import com.insurancemanagementsystem.common.event.domain.CustomerUpdatedEvent;
import com.insurancemanagementsystem.customer.entity.Customer;
import com.insurancemanagementsystem.common.messaging.MessagePublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class CustomerEventPublisher {

	private final MessagePublisher messagePublisher;

	public void publishCustomerCreated(Customer customer) {
		CustomerCreatedEvent event = CustomerCreatedEvent.builder()
			.customerId(customer.getId())
			.nationalId(customer.getNationalId())
			.email(customer.getEmail())
			.firstName(customer.getFirstName())
			.lastName(customer.getLastName())
			.build();

		EventEnvelope envelope = event.toEnvelope(null, UUID.randomUUID());
		messagePublisher.publish(EventConstants.CUSTOMER_EVENTS, envelope);
		log.info("Published CustomerCreated event for customer id: {}", customer.getId());
	}

	public void publishCustomerUpdated(Customer customer) {
		CustomerUpdatedEvent event = CustomerUpdatedEvent.builder()
			.customerId(customer.getId())
			.nationalId(customer.getNationalId())
			.email(customer.getEmail())
			.firstName(customer.getFirstName())
			.lastName(customer.getLastName())
			.build();

		EventEnvelope envelope = event.toEnvelope(null, UUID.randomUUID());
		messagePublisher.publish(EventConstants.CUSTOMER_EVENTS, envelope);
		log.info("Published CustomerUpdated event for customer id: {}", customer.getId());
	}

	public void publishCustomerDeleted(Customer customer) {
		CustomerDeletedEvent event = CustomerDeletedEvent.builder()
			.customerId(customer.getId())
			.nationalId(customer.getNationalId())
			.build();

		EventEnvelope envelope = event.toEnvelope(null, UUID.randomUUID());
		messagePublisher.publish(EventConstants.CUSTOMER_EVENTS, envelope);
		log.info("Published CustomerDeleted event for customer id: {}", customer.getId());
	}

}
