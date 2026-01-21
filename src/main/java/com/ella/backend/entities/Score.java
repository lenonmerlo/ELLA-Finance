package com.ella.backend.entities;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Table(
        name = "scores",
        indexes = {
                @Index(name = "idx_scores_person_id", columnList = "person_id"),
                @Index(name = "idx_scores_calculation_date", columnList = "calculation_date")
        }
)
@Data
public class Score {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(optional = false)
    private Person person;

    @Column(nullable = false)
    private int scoreValue;

    @Column(nullable = false)
    private LocalDate calculationDate;

    @Column(nullable = false)
    private int creditUtilizationScore;

    @Column(nullable = false)
    private int onTimePaymentScore;

    @Column(nullable = false)
    private int spendingDiversityScore;

    @Column(nullable = false)
    private int spendingConsistencyScore;

    @Column(nullable = false)
    private int creditHistoryScore;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
