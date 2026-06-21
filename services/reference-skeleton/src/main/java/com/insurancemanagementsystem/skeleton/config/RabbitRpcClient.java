package com.insurancemanagementsystem.skeleton.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class RabbitRpcClient {

    private final RabbitTemplate rabbitTemplate;

    public String sendAndReceive(String routingKey, String message) {
        log.debug("Sending RPC request with routing key '{}': {}", routingKey, message);
        Object response = rabbitTemplate.convertSendAndReceive(
                RabbitRpcConfig.RPC_EXCHANGE,
                routingKey,
                message
        );
        if (response == null) {
            log.warn("RPC request timed out for routing key '{}'", routingKey);
            return null;
        }
        String result = response.toString();
        log.debug("Received RPC response: {}", result);
        return result;
    }
}
