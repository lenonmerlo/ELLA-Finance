package com.ella.backend.email;

import lombok.Builder;
import lombok.Value;

import java.util.Map;

@Value
@Builder
public class EmailMessage {
    String to;
    String subject;
    String templateName;
    Map<String, Object> variables;
}
