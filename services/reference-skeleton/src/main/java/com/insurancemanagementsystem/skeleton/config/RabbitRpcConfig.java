package com.insurancemanagementsystem.skeleton.config;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitRpcConfig {

    public static final String RPC_EXCHANGE = "rpc-exchange";
    public static final String RPC_QUEUE = "rpc.reference-data";
    public static final String ROUTING_KEY_SAMPLE = "rpc.sample";

    @Bean
    public DirectExchange rpcExchange() {
        return new DirectExchange(RPC_EXCHANGE);
    }

    @Bean
    public Queue rpcQueue() {
        return QueueBuilder.durable(RPC_QUEUE)
                .withArgument("x-dead-letter-exchange", "dlq-exchange")
                .build();
    }

    @Bean
    public Binding rpcBinding(DirectExchange rpcExchange, Queue rpcQueue) {
        return BindingBuilder.bind(rpcQueue)
                .to(rpcExchange)
                .with(ROUTING_KEY_SAMPLE);
    }
}
