package com.ella.backend.entities;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "credit_cards")
@Data
public class CreditCard {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

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
}
