package com.insurancemanagementsystem.insurance.config;

import com.insurancemanagementsystem.common.event.EventConstants;
import com.insurancemanagementsystem.common.event.EventEnvelope;
import com.insurancemanagementsystem.common.event.domain.InsuranceCreatedEvent;
import com.insurancemanagementsystem.common.event.domain.InsuranceUpdatedEvent;
import com.insurancemanagementsystem.common.messaging.MessagePublisher;
import com.insurancemanagementsystem.insurance.entity.Insurance;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class InsuranceEventPublisher {

    private final MessagePublisher messagePublisher;

    public void publishInsuranceCreated(Insurance insurance) {
        InsuranceCreatedEvent event = InsuranceCreatedEvent.builder()
                .insuranceId(insurance.getId())
                .typeId(insurance.getTypeId())
                .companyId(insurance.getCompanyId())
                .build();

        EventEnvelope envelope = event.toEnvelope(null, UUID.randomUUID());
        messagePublisher.publish(EventConstants.INSURANCE_EVENTS, envelope);
        log.info("Published InsuranceCreated event for insurance id: {}", insurance.getId());
    }

    public void publishInsuranceUpdated(Insurance insurance) {
        InsuranceUpdatedEvent event = InsuranceUpdatedEvent.builder()
                .insuranceId(insurance.getId())
                .typeId(insurance.getTypeId())
                .companyId(insurance.getCompanyId())
                .build();

        EventEnvelope envelope = event.toEnvelope(null, UUID.randomUUID());
        messagePublisher.publish(EventConstants.INSURANCE_EVENTS, envelope);
        log.info("Published InsuranceUpdated event for insurance id: {}", insurance.getId());
    }
}
