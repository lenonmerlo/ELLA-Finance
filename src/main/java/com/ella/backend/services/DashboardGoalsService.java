package com.ella.backend.services;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.ella.backend.dto.dashboard.GoalProgressDTO;
import com.ella.backend.entities.Goal;
import com.ella.backend.entities.Person;
import com.ella.backend.exceptions.ResourceNotFoundException;
import com.ella.backend.repositories.GoalRepository;
import com.ella.backend.repositories.PersonRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DashboardGoalsService {

    private final PersonRepository personRepository;
    private final GoalRepository goalRepository;
    private final GoalGeneratorService goalGeneratorService;

    public List<GoalProgressDTO> getGoals(String personId) {
        UUID personUuid = UUID.fromString(personId);
        Person person = personRepository.findById(personUuid)
                .orElseThrow(() -> new ResourceNotFoundException("Person not found"));

        List<Goal> goals = goalRepository.findByOwner(person);

        // Se não tiver metas, gerar automaticamente (determinístico)
        if (goals == null || goals.isEmpty()) {
            goals = goalGeneratorService.generateAutomaticGoals(person, 3);
        }

        return goals.stream()
                .map(this::toProgressDTO)
                .collect(Collectors.toList());
    }

    private GoalProgressDTO toProgressDTO(Goal goal) {
        BigDecimal target = goal.getTargetAmount() != null ? goal.getTargetAmount() : BigDecimal.ZERO;
        BigDecimal current = goal.getCurrentAmount() != null ? goal.getCurrentAmount() : BigDecimal.ZERO;

        BigDecimal percentage = BigDecimal.ZERO;
        if (target.compareTo(BigDecimal.ZERO) > 0) {
            percentage = current
                    .multiply(BigDecimal.valueOf(100))
                    .divide(target, 2, RoundingMode.HALF_UP);
        }

        return GoalProgressDTO.builder()
                .goalId(goal.getId().toString())
                .title(goal.getTitle())
                .targetAmount(target)
                .currentAmount(current)
                .percentage(percentage)
                .deadline(goal.getDeadline())
                .status(goal.getStatus())
                .build();
    }
}
