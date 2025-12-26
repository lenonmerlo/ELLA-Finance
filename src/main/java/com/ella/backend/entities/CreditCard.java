package com.ella.backend.entities;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Table(
        name = "credit_cards",
        indexes = {
                @Index(
                        name = "idx_card_owner",
                        columnList = "owner_id"
                )
        }
)
@Data
public class CreditCard {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

        @Column(nullable = false)
        private String cardholderName;

    @Column(nullable = false)
    private String brand;

    @Column(length = 4)
    private String lastFourDigits;

    @Column(nullable = false)
    private BigDecimal limitAmount;

    @Column(nullable = false)
    private Integer closingDay;

    @Column(nullable = false)
    private Integer dueDay;

    @ManyToOne(optional = false)
    private Person owner;

    @OneToMany(mappedBy = "card")
    private List<Invoice> invoices;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

        @PrePersist
        @PreUpdate
        private void ensureNonNullFields() {
                if (name != null && name.isBlank()) {
                        name = name.trim();
                }

                if (cardholderName == null || cardholderName.isBlank()) {
                        if (name != null && !name.isBlank()) {
                                cardholderName = name;
                        } else if (owner != null && owner.getName() != null && !owner.getName().isBlank()) {
                                cardholderName = owner.getName();
                        } else {
                                cardholderName = "Titular";
                        }
                }
        }
}
