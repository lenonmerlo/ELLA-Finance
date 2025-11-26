package com.ella.backend.entities;

import com.ella.backend.enums.Currency;
import com.ella.backend.enums.PaymentProvider;
import com.ella.backend.enums.PaymentStatus;
import com.ella.backend.enums.Plan;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "payments")
@Getter
@Setter
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Plan plan;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Currency currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentProvider provider;

    /**
     * ID do pagamento no provedor (ex: id da preferência no Mercado Pago, paymentIntent no Stripe, etc.)
     */
    @Column(name = "provider_payment_id")
    private String providerPaymentId;

    /**
     * Status bruto retornado pelo provedor ("approved", "pending", "rejected"...)
     */
    @Column(name = "provider_raw_status")
    private String providerRawStatus;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime paidAt;
}
