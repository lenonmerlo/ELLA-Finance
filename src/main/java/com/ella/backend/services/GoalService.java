package com.ella.backend.services;

import com.ella.backend.dto.GoalRequestDTO;
import com.ella.backend.dto.GoalResponseDTO;
import com.ella.backend.entities.Goal;
import com.ella.backend.entities.Person;
import com.ella.backend.enums.GoalStatus;
import com.ella.backend.exceptions.ResourceNotFoundException;
import com.ella.backend.repositories.GoalRepository;
import com.ella.backend.repositories.PersonRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GoalService {

    private final GoalRepository goalRepository;
    private final PersonRepository personRepository;

    @CacheEvict(cacheNames = "dashboard", allEntries = true)
    public GoalResponseDTO create(GoalRequestDTO dto) {
        UUID ownerUuid = UUID.fromString(dto.getOwnerId());
        Person owner = personRepository.findById(ownerUuid)
                .orElseThrow(() -> new ResourceNotFoundException("Pessoa (owner) não encontrada"));

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

        Goal saved = goalRepository.save(goal);
        return toDTO(saved);
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

    @CacheEvict(cacheNames = "dashboard", allEntries = true)
    public GoalResponseDTO update(String id, GoalRequestDTO dto) {
        UUID uuid = UUID.fromString(id);
        Goal goal = goalRepository.findById(uuid)
                .orElseThrow(() -> new ResourceNotFoundException("Objetivo não encontrado"));

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

    @CacheEvict(cacheNames = "dashboard", allEntries = true)
    public void delete(String id) {
        UUID uuid = UUID.fromString(id);
        Goal goal = goalRepository.findById(uuid)
                .orElseThrow(() -> new ResourceNotFoundException("Objetivo não encontrado"));

        goalRepository.delete(goal);
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
