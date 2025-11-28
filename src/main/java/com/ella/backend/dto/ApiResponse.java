package com.ella.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiResponse<T> {

    private boolean success;
    private T data;
    private String message;
    private LocalDateTime timestamp;
    private List<String> errors;

    // resposta de sucesso (com dado + mensagem)
    public static <T> ApiResponse<T> success(T data, String message) {
        return ApiResponse.<T>builder()
                .success(true)
                .data(data)
                .message(message)
                .timestamp(LocalDateTime.now())
                .errors(null)
                .build();
    }

    // resposta de sucesso (só dado, sem mensagem)
    public static <T> ApiResponse<T> success(T data) {
        return success(data, null);
    }

    // resposta de sucesso (só mensagem, sem dado)
    public static <T> ApiResponse<T> message(String message) {
        return success(null, message);
    }

    // resposta de erro genérica
    public static <T> ApiResponse<T> error(String message, List<String> errors) {
        return ApiResponse.<T>builder()
                .success(false)
                .data(null)
                .message(message)
                .timestamp(LocalDateTime.now())
                .errors(errors)
                .build();
    }

    // atalho pra erro simples (sem lista de erros)
    public static <T> ApiResponse<T> error(String message) {
        return error(message, null);
    }
}
