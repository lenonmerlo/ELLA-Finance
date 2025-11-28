package com.ella.backend.controllers;

import com.ella.backend.dto.ApiResponse;
import com.ella.backend.dto.GoalRequestDTO;
import com.ella.backend.dto.GoalResponseDTO;
import com.ella.backend.services.GoalService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/goals")
@RequiredArgsConstructor
public class GoalController {

    private final GoalService goalService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or @securityService.canAccessPerson(#dto.ownerId)")
    public ResponseEntity<ApiResponse<GoalResponseDTO>> create(
            @Valid @RequestBody GoalRequestDTO dto
            ) {
        GoalResponseDTO created = goalService.create(dto);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(created, "Objetivo criado com sucesso"));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<GoalResponseDTO>>> findAll() {
        List<GoalResponseDTO> list = goalService.findAll();
        return ResponseEntity.ok(ApiResponse.success(list, "Objetivos encontrados"));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or @securityService.canAccessGoal(#id)")
    public ResponseEntity<ApiResponse<GoalResponseDTO>> findById(@PathVariable String id) {
        GoalResponseDTO dto = goalService.findById(id);
        return ResponseEntity.ok(
                ApiResponse.success(dto, "Objetivo encontrado")
        );
    }

    @GetMapping("/owner/{ownerId}")
    @PreAuthorize("hasRole('ADMIN') or @securityService.canAccessPerson(#ownerId)")
    public ResponseEntity<ApiResponse<List<GoalResponseDTO>>> findByOwner(
            @PathVariable String ownerId
    ) {
        List<GoalResponseDTO> list = goalService.findByOwner(ownerId);
        return ResponseEntity.ok(
                ApiResponse.success(list, "Objetivos do owner encontrados")
        );
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or @securityService.canAccessGoal(#id)")
    public ResponseEntity<ApiResponse<GoalResponseDTO>> update(
            @PathVariable String id,
            @Valid @RequestBody GoalRequestDTO dto
    ) {
        GoalResponseDTO updated = goalService.update(id, dto);
        return ResponseEntity.ok(
                ApiResponse.success(updated, "Objetivo atualizado com sucesso")
        );
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or @securityService.canAccessGoal(#id)")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable String id) {
        goalService.delete(id);
        return ResponseEntity.ok(
                ApiResponse.success(null, "Objetivo deletado com sucesso")
        );
    }
}
