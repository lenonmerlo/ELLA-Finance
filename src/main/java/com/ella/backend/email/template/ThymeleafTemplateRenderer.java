package com.ella.backend.email.template;

import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

@Component
public class ThymeleafTemplateRenderer implements TemplateRenderer {

    private final TemplateEngine templateEngine;

    public ThymeleafTemplateRenderer(TemplateEngine templateEngine) {
        this.templateEngine = templateEngine;
    }

    @Override
    public String render(String templateName, Map<String, Object> variables) {
        Context context = new Context(Locale.of("pt", "BR"));

        if (variables != null) {
            variables.forEach(context::setVariable);
        }

        return templateEngine.process("email/" + templateName, context);
    }
}
