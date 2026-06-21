package com.insurancemanagementsystem.skeleton.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SampleRequest {
    @NotBlank(message = "Name is required")
    private String name;
}
