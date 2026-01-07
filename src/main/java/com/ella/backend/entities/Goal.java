package com.ella.backend.entities;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.ella.backend.enums.GoalDifficulty;
import com.ella.backend.enums.GoalStatus;
import com.ella.backend.enums.GoalTimeframe;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "goals",
        indexes = {
                @Index(
                        name = "idx_goal_user_status",
                        columnList = "owner_id, status"
                )
        }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Goal {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column
    private String category;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal targetAmount;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal currentAmount = BigDecimal.ZERO;

    @Column(precision = 19, scale = 2)
    private BigDecimal savingsPotential;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private GoalDifficulty difficulty;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private GoalTimeframe timeframe;

    private LocalDate deadline;

    @Column
    private LocalDate targetDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private GoalStatus status = GoalStatus.ACTIVE;

    @ManyToOne(optional = false)
    private Person owner;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    /**
     * Calcula a data alvo baseada no timeframe
     */
    public void calculateTargetDate() {
        if (this.deadline == null) {
            this.deadline = LocalDate.now();
        }

        this.targetDate = switch (this.timeframe) {
            case ONE_WEEK -> this.deadline.plusWeeks(1);
            case TWO_WEEKS -> this.deadline.plusWeeks(2);
            case ONE_MONTH -> this.deadline.plusMonths(1);
            case THREE_MONTHS -> this.deadline.plusMonths(3);
        };
    }

    @PrePersist
    public void prePersist() {
        if (this.deadline == null) {
            this.deadline = LocalDate.now();
        }
        if (this.status == null) {
            this.status = GoalStatus.ACTIVE;
        }
        if (this.currentAmount == null) {
            this.currentAmount = BigDecimal.ZERO;
        }
        if (this.difficulty == null) {
            this.difficulty = GoalDifficulty.MEDIUM;
        }
        if (this.timeframe == null) {
            this.timeframe = GoalTimeframe.ONE_MONTH;
        }
        calculateTargetDate();
    }
}
