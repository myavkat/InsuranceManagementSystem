package com.insurancemanagementsystem.estimation.config;

import com.insurancemanagementsystem.common.messaging.MessagePublisher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.stream.function.StreamBridge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MessagePublisherTest {

    @Mock
    private StreamBridge streamBridge;

    @InjectMocks
    private MessagePublisher messagePublisher;

    @Captor
    private ArgumentCaptor<Object> messageCaptor;

    @Test
    void publish_sendsViaStreamBridge() {
        String topic = "test.topic";
        Object payload = "test-payload";
        when(streamBridge.send(topic, payload)).thenReturn(true);

        messagePublisher.publish(topic, payload);

        verify(streamBridge).send(topic, payload);
    }

    @Test
    void publish_failedSend_throwsIllegalStateException() {
        String topic = "test.topic";
        Object payload = "test-payload";
        when(streamBridge.send(topic, payload)).thenReturn(false);

        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> messagePublisher.publish(topic, payload));
    }
}
