package com.ella.backend.entities;

import com.ella.backend.enums.Currency;
import com.ella.backend.enums.Language;
import com.ella.backend.enums.Plan;
import com.ella.backend.enums.Status;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "persons")
@Data
@Inheritance(strategy = InheritanceType.JOINED)
public class Person {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String name;

    private String phone;
    private LocalDate birthDate;
    private String address;
    private BigDecimal income;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Language language = Language.PT_BR;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Plan plan = Plan.FREE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Currency currency = Currency.BRL;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.ACTIVE;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

}
