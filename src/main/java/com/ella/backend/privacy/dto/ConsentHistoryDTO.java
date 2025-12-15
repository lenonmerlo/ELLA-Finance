package com.ella.backend.privacy.dto;

import java.time.Instant;

public record ConsentHistoryDTO (String contractVersion,
                                 Instant acceptedAt,
                                 String ip) {}
