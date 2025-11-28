package com.ella.backend.controllers;

import com.ella.backend.dto.ApiResponse;
import com.ella.backend.dto.CreditCardRequestDTO;
import com.ella.backend.dto.CreditCardResponseDTO;
import com.ella.backend.services.CreditCardService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/credit-cards")
@RequiredArgsConstructor
public class CreditCardController {

    private final CreditCardService creditCardService;

    /**
     * Criar cartão de crédito.
     * ADMIN: pode criar para qualquer pessoa.
     * USER: só pode criar cartão para ele mesmo.
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or @securityService.canAccessPerson(#dto.ownerId)")
    public ResponseEntity<ApiResponse<CreditCardResponseDTO>> create(
            @Valid @RequestBody CreditCardRequestDTO dto
    ) {
        CreditCardResponseDTO created = creditCardService.create(dto);
        return ResponseEntity
                .status(201)
                .body(ApiResponse.success(created, "Cartão criado com sucesso"));
    }

    /**
     * Recuperar cartão por ID.
     * ADMIN: pode tudo.
     * USER: só vê cartão se for dele.
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or @securityService.canAccessCreditCard(#id)")
    public ResponseEntity<ApiResponse<CreditCardResponseDTO>> findById(@PathVariable String id) {
        CreditCardResponseDTO found = creditCardService.findById(id);
        return ResponseEntity.ok(
                ApiResponse.success(found, "Cartão encontrado")
        );
    }

    /**
     * Listar todos os cartões.
     * Apenas ADMIN, pois é dado extremamente sensível.
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<CreditCardResponseDTO>>> findAll() {
        List<CreditCardResponseDTO> list = creditCardService.findAll();
        return ResponseEntity.ok(
                ApiResponse.success(list, "Cartões encontrados")
        );
    }

    /**
     * Listar cartões por owner.
     * ADMIN: pode tudo.
     * USER: apenas se o ownerId for dele.
     */
    @GetMapping("/owner/{ownerId}")
    @PreAuthorize("hasRole('ADMIN') or @securityService.canAccessPerson(#ownerId)")
    public ResponseEntity<ApiResponse<List<CreditCardResponseDTO>>> findByOwner(
            @PathVariable String ownerId
    ) {
        List<CreditCardResponseDTO> list = creditCardService.findByOwner(ownerId);
        return ResponseEntity.ok(
                ApiResponse.success(list, "Cartões do cliente encontrados")
        );
    }

    /**
     * Atualizar cartão.
     * ADMIN: pode tudo.
     * USER: só se for dono do cartão.
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or @securityService.canAccessCreditCard(#id)")
    public ResponseEntity<ApiResponse<CreditCardResponseDTO>> update(
            @PathVariable String id,
            @Valid @RequestBody CreditCardRequestDTO dto
    ) {
        CreditCardResponseDTO updated = creditCardService.update(id, dto);
        return ResponseEntity.ok(
                ApiResponse.success(updated, "Cartão atualizado com sucesso")
        );
    }

    /**
     * Remover cartão.
     * ADMIN: pode tudo.
     * USER: só se for dono do cartão.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or @securityService.canAccessCreditCard(#id)")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable String id) {
        creditCardService.delete(id);
        return ResponseEntity.ok(
                ApiResponse.success(null, "Cartão removido com sucesso")
        );
    }
}