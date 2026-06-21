package com.insurancemanagementsystem.customer.config;

import com.insurancemanagementsystem.common.event.EventConstants;
import com.insurancemanagementsystem.common.event.domain.CustomerCreatedEvent;
import com.insurancemanagementsystem.common.event.domain.CustomerUpdatedEvent;
import com.insurancemanagementsystem.customer.entity.Customer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

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
                .build();

        messagePublisher.publish(EventConstants.CUSTOMER_EVENTS, event);
        log.info("Published CustomerCreated event for customer id: {}", customer.getId());
    }

    public void publishCustomerUpdated(Customer customer) {
        CustomerUpdatedEvent event = CustomerUpdatedEvent.builder()
                .customerId(customer.getId())
                .nationalId(customer.getNationalId())
                .email(customer.getEmail())
                .build();

        messagePublisher.publish(EventConstants.CUSTOMER_EVENTS, event);
        log.info("Published CustomerUpdated event for customer id: {}", customer.getId());
    }
}
