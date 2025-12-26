package com.ella.backend.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ella.backend.dto.ApiResponse;
import com.ella.backend.dto.ApplyTripRequestDTO;
import com.ella.backend.dto.ApplyTripResponseDTO;
import com.ella.backend.services.TripService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/trips")
@RequiredArgsConstructor
public class TripController {

    private final TripService tripService;

    @PostMapping("/apply")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<ApplyTripResponseDTO>> apply(@RequestBody ApplyTripRequestDTO request) {
        try {
            ApplyTripResponseDTO payload = tripService.applyTrip(request);
            return ResponseEntity.ok(ApiResponse.success(payload, "Viagem aplicada com sucesso"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }
}
