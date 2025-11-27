package com.ella.backend.services;

import com.ella.backend.dto.GoalRequestDTO;
import com.ella.backend.dto.GoalResponseDTO;
import com.ella.backend.entities.Goal;
import com.ella.backend.entities.Person;
import com.ella.backend.enums.GoalStatus;
import com.ella.backend.exceptions.BadRequestException;
import com.ella.backend.exceptions.ResourceNotFoundException;
import com.ella.backend.repositories.GoalRepository;
import com.ella.backend.repositories.PersonRepository;
import com.ella.backend.audit.Auditable;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GoalService {

    private final GoalRepository goalRepository;
    private final PersonRepository personRepository;

    @Auditable(action = "GOAL_CREATED", entityType = "Goal")
    @CacheEvict(cacheNames = "dashboard", allEntries = true)
    public GoalResponseDTO create(GoalRequestDTO dto) {
        UUID ownerUuid = UUID.fromString(dto.getOwnerId());
        Person owner = personRepository.findById(ownerUuid)
                .orElseThrow(() -> new ResourceNotFoundException("Pessoa (owner) não encontrada"));

        BigDecimal currentAmount = dto.getCurrentAmount() != null
                ? dto.getCurrentAmount()
                : BigDecimal.ZERO;

        validateGoalBusinessRules(dto.getTargetAmount(), currentAmount, dto.getDeadline());

        Goal goal = getGoal(dto, owner);

        Goal saved = goalRepository.save(goal);
        return toDTO(saved);
    }

    private static Goal getGoal(GoalRequestDTO dto, Person owner) {
        Goal goal = new Goal();
        goal.setTitle(dto.getTitle());
        goal.setDescription(dto.getDescription());
        goal.setTargetAmount(dto.getTargetAmount());
        goal.setCurrentAmount(
                dto.getCurrentAmount() != null ? dto.getCurrentAmount() : BigDecimal.ZERO
        );
        goal.setDeadline(dto.getDeadline());
        goal.setOwner(owner);

        if (dto.getStatus() != null) {
            goal.setStatus(dto.getStatus());
        } else {
            goal.setStatus(GoalStatus.ACTIVE);
        }
        return goal;
    }

    public List<GoalResponseDTO> findAll() {
        return goalRepository.findAll()
                .stream()
                .map(this::toDTO)
                .toList();
    }

    public GoalResponseDTO findById(String id) {
        UUID uuid = UUID.fromString(id);
        Goal goal = goalRepository.findById(uuid)
                .orElseThrow(() -> new ResourceNotFoundException("Objetivo não encontrado"));

        return toDTO(goal);
    }

    public List<GoalResponseDTO> findByOwner(String ownerId) {
        UUID ownerUuid = UUID.fromString(ownerId);
        Person owner = personRepository.findById(ownerUuid)
                .orElseThrow(() -> new ResourceNotFoundException("Pessoa (owner) não encontrada"));

        return goalRepository.findByOwner(owner)
                .stream()
                .map(this::toDTO)
                .toList();
    }

    @Auditable(action = "GOAL_UPDATED", entityType = "Goal")
    @CacheEvict(cacheNames = "dashboard", allEntries = true)
    public GoalResponseDTO update(String id, GoalRequestDTO dto) {
        UUID uuid = UUID.fromString(id);
        Goal goal = goalRepository.findById(uuid)
                .orElseThrow(() -> new ResourceNotFoundException("Objetivo não encontrado"));

        // Calcula o "estado final" para validar
        BigDecimal targetAmount = dto.getTargetAmount() != null
                ? dto.getTargetAmount()
                : goal.getTargetAmount();

        BigDecimal currentAmount = dto.getCurrentAmount() != null
                ? dto.getCurrentAmount()
                : goal.getCurrentAmount();

        LocalDate deadline = dto.getDeadline() != null
                ? dto.getDeadline()
                : goal.getDeadline();

        validateGoalBusinessRules(targetAmount, currentAmount, deadline);

        goal.setTitle(dto.getTitle());
        goal.setDescription(dto.getDescription());
        goal.setTargetAmount(dto.getTargetAmount());

        if (dto.getCurrentAmount() != null) {
            goal.setCurrentAmount(dto.getCurrentAmount());
        }

        goal.setDeadline(dto.getDeadline());

        if (dto.getStatus() != null) {
            goal.setStatus(dto.getStatus());
        }

        if (dto.getOwnerId() != null && !dto.getOwnerId().isBlank()) {
            UUID ownerUuid = UUID.fromString(dto.getOwnerId());
            Person owner = personRepository.findById(ownerUuid)
                    .orElseThrow(() -> new ResourceNotFoundException("Pessoa (owner) não encontrada"));
            goal.setOwner(owner);
        }

        Goal saved = goalRepository.save(goal);
        return toDTO(saved);
    }

    @Auditable(action = "GOAL_DELETED", entityType = "Goal")
    @CacheEvict(cacheNames = "dashboard", allEntries = true)
    public void delete(String id) {
        UUID uuid = UUID.fromString(id);
        Goal goal = goalRepository.findById(uuid)
                .orElseThrow(() -> new ResourceNotFoundException("Objetivo não encontrado"));

        goalRepository.delete(goal);
    }

    private void validateGoalBusinessRules(BigDecimal targetAmount, BigDecimal currentAmount, LocalDate deadline) {
        if (targetAmount == null || targetAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Valor alvo da meta deve ser maior que zero");
        }

        if (currentAmount != null && currentAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new BadRequestException("Valor atual da meta não pode ser negativo");
        }

        if (deadline != null && deadline.isBefore(LocalDate.now())) {
            throw new BadRequestException("Prazo da meta não pode estar no passado");
        }
    }

    private GoalResponseDTO toDTO(Goal goal) {
        GoalResponseDTO dto = new GoalResponseDTO();

        dto.setId(goal.getId().toString());
        dto.setTitle(goal.getTitle());
        dto.setDescription(goal.getDescription());
        dto.setTargetAmount(goal.getTargetAmount());
        dto.setCurrentAmount(goal.getCurrentAmount());
        dto.setDeadline(goal.getDeadline());
        dto.setStatus(goal.getStatus());
        dto.setOwnerId(goal.getOwner().getId().toString());
        dto.setOwnerName(goal.getOwner().getName());
        dto.setCreatedAt(goal.getCreatedAt());
        dto.setUpdatedAt(goal.getUpdatedAt());

        return dto;
    }
}
