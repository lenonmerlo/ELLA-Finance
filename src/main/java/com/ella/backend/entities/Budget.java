package com.ella.backend.entities;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "budgets",
        indexes = {
                @Index(name = "idx_budget_owner", columnList = "owner_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Budget {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private Person owner;

    // Campos de entrada
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal income;

    @Column(name = "essential_fixed_cost", nullable = false, precision = 19, scale = 2)
    private BigDecimal essentialFixedCost;

    @Column(name = "necessary_fixed_cost", nullable = false, precision = 19, scale = 2)
    private BigDecimal necessaryFixedCost;

    @Column(name = "variable_fixed_cost", nullable = false, precision = 19, scale = 2)
    private BigDecimal variableFixedCost;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal investment;

    @Column(name = "planned_purchase", nullable = false, precision = 19, scale = 2)
    private BigDecimal plannedPurchase;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal protection;

    // Campos calculados
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal total;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal balance;

    // Regra 50/30/20
    @Column(name = "necessities_percentage", nullable = false, precision = 5, scale = 2)
    private BigDecimal necessitiesPercentage;

    @Column(name = "desires_percentage", nullable = false, precision = 5, scale = 2)
    private BigDecimal desiresPercentage;

    @Column(name = "investments_percentage", nullable = false, precision = 5, scale = 2)
    private BigDecimal investmentsPercentage;

    // Auditoria
    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
