package com.ella.backend.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class ApiResponse<T> {

    private boolean success;
    private T data;
    private String message;
    private Instant timestamp;
    private List<String> errors;
}

