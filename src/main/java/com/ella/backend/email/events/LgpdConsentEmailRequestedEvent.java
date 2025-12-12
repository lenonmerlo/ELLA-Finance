package com.ella.backend.email.events;

import java.util.UUID;

public record LgpdConsentEmailRequestedEvent(UUID userId, String name, String email) {
}
