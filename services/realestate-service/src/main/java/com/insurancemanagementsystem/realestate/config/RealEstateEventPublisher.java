package com.insurancemanagementsystem.realestate.config;

import com.insurancemanagementsystem.common.event.EventConstants;
import com.insurancemanagementsystem.common.event.EventEnvelope;
import com.insurancemanagementsystem.common.event.domain.RealEstateCreatedEvent;
import com.insurancemanagementsystem.common.event.domain.RealEstateDeletedEvent;
import com.insurancemanagementsystem.common.event.domain.RealEstateUpdatedEvent;
import com.insurancemanagementsystem.common.messaging.MessagePublisher;
import com.insurancemanagementsystem.realestate.entity.RealEstate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class RealEstateEventPublisher {

    private final MessagePublisher messagePublisher;

    public void publishRealEstateCreated(RealEstate realEstate) {
        RealEstateCreatedEvent event = RealEstateCreatedEvent.builder()
                .realEstateId(realEstate.getId())
                .build();
        EventEnvelope envelope = event.toEnvelope(null, UUID.randomUUID());
        messagePublisher.publish(EventConstants.REALESTATE_EVENTS, envelope);
        log.info("Published RealEstateCreated event for id: {}", realEstate.getId());
    }

    public void publishRealEstateUpdated(RealEstate realEstate) {
        RealEstateUpdatedEvent event = RealEstateUpdatedEvent.builder()
                .realEstateId(realEstate.getId())
                .build();
        EventEnvelope envelope = event.toEnvelope(null, UUID.randomUUID());
        messagePublisher.publish(EventConstants.REALESTATE_EVENTS, envelope);
        log.info("Published RealEstateUpdated event for id: {}", realEstate.getId());
    }

    public void publishRealEstateDeleted(RealEstate realEstate) {
        RealEstateDeletedEvent event = RealEstateDeletedEvent.builder()
                .realEstateId(realEstate.getId())
                .build();
        EventEnvelope envelope = event.toEnvelope(null, UUID.randomUUID());
        messagePublisher.publish(EventConstants.REALESTATE_EVENTS, envelope);
        log.info("Published RealEstateDeleted event for id: {}", realEstate.getId());
    }
}
