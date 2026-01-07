package com.ella.backend.entities;

import java.time.LocalDate;

import com.ella.backend.enums.InsightSeverity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "insights", indexes = {
    @Index(name = "idx_insight_user_date", columnList = "user_id,generated_at")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Insight {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column
    private String category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InsightSeverity severity;

    @Column(nullable = false)
    private boolean actionable;

    @Column
    private LocalDate generatedAt;

    @Column
    private LocalDate startDate;

    @Column
    private LocalDate endDate;
}
