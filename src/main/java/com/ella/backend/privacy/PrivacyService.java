package com.ella.backend.privacy;

import com.ella.backend.privacy.dto.ConsentRequestDTO;
import com.ella.backend.privacy.dto.ConsentResponseDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PrivacyService {

    private final ConsentLogRepository consentLogRepository;

    @Value("${lgpd.contract.version:2025-12-12-v1}")
    private String currentContractVersion;

    public ConsentResponseDTO registerConsent(UUID userId, String ip, ConsentRequestDTO dto) {
        String version = (dto.contractVersion() == null || dto.contractVersion().isBlank())
                ? currentContractVersion : dto.contractVersion();

        ConsentLog log = ConsentLog.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .ip(ip)
                .contractVersion(version)
                .acceptedAt(Instant.now())
                .build();

        consentLogRepository.save(log);

        return new ConsentResponseDTO(log.getContractVersion(), log.getAcceptedAt());
    }

    public boolean hasAnyConsent(UUID userId) {
        return !consentLogRepository.findByUserIdOrderByAcceptedAtDesc(userId).isEmpty();
    }

}
