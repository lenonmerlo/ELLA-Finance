package com.ella.backend.controllers;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ella.backend.dto.ApiResponse;
import com.ella.backend.dto.GoalDTO;
import com.ella.backend.dto.GoalRequestDTO;
import com.ella.backend.dto.GoalResponseDTO;
import com.ella.backend.entities.Goal;
import com.ella.backend.entities.Person;
import com.ella.backend.enums.GoalStatus;
import com.ella.backend.repositories.GoalRepository;
import com.ella.backend.services.GoalService;
import com.ella.backend.services.ai.GoalSuggestionService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/goals")
@RequiredArgsConstructor
@Slf4j
public class GoalController {

    private final GoalService goalService;
    private final GoalSuggestionService goalSuggestionService;
    private final GoalRepository goalRepository;

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

    /**
     * Sugere novas metas para o usuário autenticado baseado em insights e histórico
     */
    @PostMapping("/suggest")
    public ResponseEntity<List<GoalDTO>> suggestGoals(Authentication authentication) {
        Person owner = getCurrentUser(authentication);

        log.info("[GoalController] Sugerindo metas para usuário {}", owner.getId());

        List<Goal> goals = goalSuggestionService.suggestGoals(owner);
        List<GoalDTO> dtos = goals.stream()
            .map(this::toDTO)
            .collect(Collectors.toList());

        return ResponseEntity.ok(dtos);
    }

    /**
     * Marca uma meta como concluída
     */
    @PutMapping("/{id}/complete")
    public ResponseEntity<GoalDTO> completeGoal(
        @PathVariable String id,
        Authentication authentication
    ) {
        Person owner = getCurrentUser(authentication);
        UUID goalId = UUID.fromString(id);

        log.info("[GoalController] Marcando meta {} como concluída", goalId);

        Goal goal = goalRepository.findByIdAndOwner(goalId, owner)
            .orElseThrow(() -> new RuntimeException("Goal not found"));

        goal.setStatus(GoalStatus.COMPLETED);
        goal = goalRepository.save(goal);

        return ResponseEntity.ok(toDTO(goal));
    }

    /**
     * Marca uma meta como abandonada
     */
    @PutMapping("/{id}/abandon")
    public ResponseEntity<GoalDTO> abandonGoal(
        @PathVariable String id,
        Authentication authentication
    ) {
        Person owner = getCurrentUser(authentication);
        UUID goalId = UUID.fromString(id);

        log.info("[GoalController] Marcando meta {} como abandonada", goalId);

        Goal goal = goalRepository.findByIdAndOwner(goalId, owner)
            .orElseThrow(() -> new RuntimeException("Goal not found"));

        goal.setStatus(GoalStatus.ABANDONED);
        goal = goalRepository.save(goal);

        return ResponseEntity.ok(toDTO(goal));
    }

    private GoalDTO toDTO(Goal goal) {
        return new GoalDTO(
            goal.getId().toString(),
            goal.getTitle(),
            goal.getDescription(),
            goal.getCategory(),
            goal.getTargetAmount(),
            goal.getCurrentAmount(),
            goal.getSavingsPotential(),
            goal.getDifficulty().getDisplayName(),
            goal.getTimeframe().getDisplayName(),
            goal.getDeadline(),
            goal.getTargetDate(),
            goal.getStatus().getDisplayName()
        );
    }

    private Person getCurrentUser(Authentication authentication) {
        return (Person) authentication.getPrincipal();
    }
}
