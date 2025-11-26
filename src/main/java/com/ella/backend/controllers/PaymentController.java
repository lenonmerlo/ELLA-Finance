// Rotas: /api/payments/**
package com.ella.backend.controllers;

import com.ella.backend.dto.payment.PaymentResponseDTO;
import com.ella.backend.dto.payment.PaymentSimulationRequestDTO;
import com.ella.backend.services.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    /**
     * V1: simula um pagamento aprovado e libera o plano para o usuário.
     * Futuro: este endpoint pode ser substituído por um flow real de checkout.
     */
    @PostMapping("/simulate")
    public ResponseEntity<PaymentResponseDTO> simulatePayment(
            @Valid @RequestBody PaymentSimulationRequestDTO dto
    ) {
        PaymentResponseDTO response = paymentService.simulatePayment(dto);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<PaymentResponseDTO>> findByUser(@PathVariable String userId) {
        List<PaymentResponseDTO> payments = paymentService.findByUser(userId);
        return ResponseEntity.ok(payments);
    }
}
