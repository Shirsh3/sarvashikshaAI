package com.sarvashikshaai.model;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class TeachingRequest {

    @NotBlank
    private String topic;
}
