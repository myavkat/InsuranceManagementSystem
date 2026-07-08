package com.insurancemanagementsystem.gateway.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {
    private boolean success;
    private String message;
    private Object data;
    private Instant timestamp;

    public static ErrorResponse of(String message) {
        return ErrorResponse.builder()
                .success(false)
                .message(message)
                .timestamp(Instant.now())
                .build();
    }

    public static ErrorResponse of(String message, Object data) {
        return ErrorResponse.builder()
                .success(false)
                .message(message)
                .data(data)
                .timestamp(Instant.now())
                .build();
    }
}
