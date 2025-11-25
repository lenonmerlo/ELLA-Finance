package com.ella.backend.controllers;

import com.ella.backend.dto.ApiResponse;
import com.ella.backend.dto.CreditCardRequestDTO;
import com.ella.backend.dto.CreditCardResponseDTO;
import com.ella.backend.services.CreditCardService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/credit-cards")
@RequiredArgsConstructor
public class CreditCardController {

    private final CreditCardService creditCardService;

    @PostMapping
    public ResponseEntity<ApiResponse<CreditCardResponseDTO>> create(
            @Valid @RequestBody CreditCardRequestDTO dto
    ) {
        CreditCardResponseDTO created = creditCardService.create(dto);
        return ResponseEntity
                .status(201)
                .body(ApiResponse.success(created, "Cartão criado com sucesso"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<CreditCardResponseDTO>> findById(@PathVariable String id) {
        CreditCardResponseDTO found = creditCardService.findById(id);
        return ResponseEntity.ok(
                ApiResponse.success(found, "Cartão encontrado")
        );
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<CreditCardResponseDTO>>> findAll() {
        List<CreditCardResponseDTO> list = creditCardService.findAll();
        return ResponseEntity.ok(
                ApiResponse.success(list, "Cartões encontrados")
        );
    }

    @GetMapping("/owner/{ownerId}")
    public ResponseEntity<ApiResponse<List<CreditCardResponseDTO>>> findByOwner(
            @PathVariable String ownerId
    ) {
        List<CreditCardResponseDTO> list = creditCardService.findByOwner(ownerId);
        return ResponseEntity.ok(
                ApiResponse.success(list, "Cartões do cliente encontrados")
        );
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<CreditCardResponseDTO>> update(
            @PathVariable String id,
            @Valid @RequestBody CreditCardRequestDTO dto
    ) {
        CreditCardResponseDTO updated = creditCardService.update(id, dto);
        return ResponseEntity.ok(
                ApiResponse.success(updated, "Cartão atualizado com sucesso")
        );
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable String id) {
        creditCardService.delete(id);
        return ResponseEntity.ok(
                ApiResponse.success(null, "Cartão removido com sucesso")
        );
    }
}