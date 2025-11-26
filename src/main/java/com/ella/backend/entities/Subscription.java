package com.ella.backend.entities;

import com.ella.backend.enums.Plan;
import com.ella.backend.enums.SubscriptionStatus;
import jakarta.persistence.*;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "subscriptions")
@Getter
@Setter
@Data
public class Subscription {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", unique = true)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SubscriptionStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Plan plan;

    @Column(nullable = false)
    private LocalDate startDate;

    @Column(nullable = false)
    private LocalDate endDate;

    /**
     * True, se no futuro implementar renovação automática.
     */
    @Column(nullable = false)
    private boolean autoRenew;

}
