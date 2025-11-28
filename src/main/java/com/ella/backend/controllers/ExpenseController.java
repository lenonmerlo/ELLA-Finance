// src/main/java/com/ella/backend/controllers/ExpenseController.java
// Rota: /api/expenses
package com.ella.backend.controllers;

import com.ella.backend.dto.ApiResponse;
import com.ella.backend.dto.ExpenseRequestDTO;
import com.ella.backend.dto.ExpenseResponseDTO;
import com.ella.backend.services.ExpenseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;


import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/expenses")
@RequiredArgsConstructor
public class ExpenseController {

    private final ExpenseService expenseService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or @securityService.canAccessPerson(#dto.personId())")
    public ResponseEntity<ApiResponse<ExpenseResponseDTO>> create(
            @Valid @RequestBody ExpenseRequestDTO dto
    ) {
        ExpenseResponseDTO created = expenseService.create(dto);
        return ResponseEntity
                .status(201)
                .body(ApiResponse.success(created, "Despesa criada com sucesso"));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or @securityService.canAccessExpense(#id)")
    public ResponseEntity<ApiResponse<ExpenseResponseDTO>> findById(@PathVariable String id) {
        ExpenseResponseDTO found = expenseService.findById(id);
        return ResponseEntity.ok(
                ApiResponse.success(found, "Despesa encontrada")
        );
    }

    @GetMapping("/person/{personId}")
    @PreAuthorize("hasRole('ADMIN') or @securityService.canAccessPerson(#personId)")
    public ResponseEntity<ApiResponse<Page<ExpenseResponseDTO>>> findByPerson(
            @PathVariable String personId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        Page<ExpenseResponseDTO> result = expenseService.findByPersonPaginated(personId, page, size);
        return ResponseEntity.ok(ApiResponse.success(result, "Despesas carregadas com sucesso"));
    }

    @GetMapping("/person/{personId}/period")
    @PreAuthorize("hasRole('ADMIN') or @securityService.canAccessPerson(#personId)")
    public ResponseEntity<ApiResponse<List<ExpenseResponseDTO>>> findByPersonAndPeriod(
            @PathVariable String personId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end
    ) {
        List<ExpenseResponseDTO> list = expenseService.findByPersonAndPeriod(personId, start, end);
        return ResponseEntity.ok(
                ApiResponse.success(list, "Despesas encontradas")
        );
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or @securityService.canAccessExpense(#id)")
    public ResponseEntity<ApiResponse<ExpenseResponseDTO>> update(
            @PathVariable String id,
            @Valid @RequestBody ExpenseRequestDTO dto
    ) {
        ExpenseResponseDTO updated = expenseService.update(id, dto);
        return ResponseEntity.ok(
                ApiResponse.success(updated, "Despesa atualizada com sucesso")
        );
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or @securityService.canAccessExpense(#id)")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable String id) {
        expenseService.delete(id);
        return ResponseEntity.ok(
                ApiResponse.success(null, "Despesa removida com sucesso")
        );
    }
}
