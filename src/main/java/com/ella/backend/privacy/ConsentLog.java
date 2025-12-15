package com.ella.backend.privacy;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "consent_log")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class ConsentLog {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "ip", nullable = false)
    private String ip;

    @Column(name = "contract_version", nullable = false)
    private String contractVersion;

    @Column(name = "accepted_at", nullable = false)
    private Instant acceptedAt;
}
