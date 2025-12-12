package com.ella.backend.email.events;

import java.util.UUID;

public record UserRegisteredEvent(UUID userId, String name, String email) {
}
