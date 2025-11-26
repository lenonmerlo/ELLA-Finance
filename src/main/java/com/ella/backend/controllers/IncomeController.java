package com.ella.backend.controllers;

import com.ella.backend.dto.ApiResponse;
import com.ella.backend.dto.IncomeRequestDTO;
import com.ella.backend.dto.IncomeResponseDTO;
import com.ella.backend.services.IncomeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;

import java.util.List;

@RestController
@RequestMapping("/api/incomes")
@RequiredArgsConstructor
public class IncomeController {

    private final IncomeService incomeService;

    @PostMapping
    public ResponseEntity<ApiResponse<IncomeResponseDTO>> create(@Valid @RequestBody IncomeRequestDTO dto) {
        IncomeResponseDTO created = incomeService.create(dto);

        return ResponseEntity.status(201).body(ApiResponse.success
                (created, "Receita criada com sucesso"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<IncomeResponseDTO>> findById(@PathVariable String id) {
        IncomeResponseDTO found = incomeService.findById(id);

        return ResponseEntity
                .ok(ApiResponse.success(found, "Receita encontrada"));
    }

    @GetMapping("/person/{personId}")
    public ResponseEntity<ApiResponse<Page<IncomeResponseDTO>>> findByPerson(
            @PathVariable String personId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        Page<IncomeResponseDTO> result = incomeService.findByPersonPaginated(personId, page, size);
        return ResponseEntity.ok(ApiResponse.success(result, "Receitas carregadas com sucesso"));
    }

    @GetMapping("/person/{personId}/period")
    public ResponseEntity<ApiResponse<List<IncomeResponseDTO>>> findByPersonAndPeriod(
            @PathVariable String personId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end
            ) {
        List<IncomeResponseDTO> list = incomeService.findByPersonAndPeriod(personId, start, end);

        return ResponseEntity.ok(ApiResponse.success(list, "Receitas encontradas"));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<IncomeResponseDTO>> update(
            @PathVariable String id,
            @Valid @RequestBody IncomeRequestDTO dto) {
        IncomeResponseDTO updated = incomeService.update(id, dto);

        return ResponseEntity.ok(ApiResponse.success(updated, "Receita atualizada"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable String id) {
        incomeService.delete(id);

        return ResponseEntity.ok(ApiResponse.success(null, "Receita removida"));
    }
}
