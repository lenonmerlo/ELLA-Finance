package com.ella.backend.classification;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ella.backend.classification.dto.CategoryRuleCreateRequestDTO;
import com.ella.backend.classification.dto.CategoryRuleResponseDTO;
import com.ella.backend.classification.dto.ClassificationFeedbackRequestDTO;
import com.ella.backend.classification.dto.ClassificationSuggestRequestDTO;
import com.ella.backend.classification.dto.ClassificationSuggestResponseDTO;
import com.ella.backend.classification.entity.CategoryFeedback;
import com.ella.backend.classification.entity.CategoryRule;
import com.ella.backend.classification.repository.CategoryFeedbackRepository;
import com.ella.backend.classification.repository.CategoryRuleRepository;
import com.ella.backend.dto.ApiResponse;
import com.ella.backend.entities.User;
import com.ella.backend.services.UserService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/classification")
@RequiredArgsConstructor
public class ClassificationController {

    private final ClassificationService classificationService;
    private final UserService userService;
    private final CategoryFeedbackRepository feedbackRepository;
    private final CategoryRuleRepository ruleRepository;

    @PostMapping("/suggest")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<ClassificationSuggestResponseDTO>> suggest(
            @Valid @RequestBody ClassificationSuggestRequestDTO request
    ) {
        User user = getCurrentUser();
        var suggestion = classificationService.suggest(user.getId(), request.description(), request.amount(), request.type());
        return ResponseEntity.ok(ApiResponse.success(suggestion, "Sugestão gerada"));
    }

    @PostMapping("/feedback")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> feedback(
            @Valid @RequestBody ClassificationFeedbackRequestDTO request
    ) {
        User user = getCurrentUser();

        UUID txId = UUID.fromString(request.transactionId());

        feedbackRepository.save(CategoryFeedback.builder()
                .userId(user.getId())
                .transactionId(txId)
                .suggestedCategory(request.suggestedCategory())
                .chosenCategory(request.chosenCategory())
                .confidence(request.confidence())
                .build());

        return ResponseEntity.ok(ApiResponse.success(null, "Feedback registrado"));
    }

    @GetMapping("/rules")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<CategoryRuleResponseDTO>>> listRules() {
        User user = getCurrentUser();
        List<CategoryRuleResponseDTO> rules = ruleRepository.findByUserIdOrderByPriorityDescCreatedAtDesc(user.getId())
                .stream()
                .map(r -> new CategoryRuleResponseDTO(
                        r.getId().toString(),
                        r.getPattern(),
                        r.getCategory(),
                        r.getPriority(),
                        r.getCreatedAt()
                ))
                .toList();

        return ResponseEntity.ok(ApiResponse.success(rules, "Regras encontradas"));
    }

    @PostMapping("/rules")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<CategoryRuleResponseDTO>> createRule(
            @Valid @RequestBody CategoryRuleCreateRequestDTO request
    ) {
        User user = getCurrentUser();
        int priority = request.priority() != null ? request.priority() : 0;

        CategoryRule saved = ruleRepository.save(CategoryRule.builder()
                .userId(user.getId())
                .pattern(request.pattern())
                .category(request.category())
                .priority(priority)
                .build());

        return ResponseEntity.status(201).body(ApiResponse.success(
                new CategoryRuleResponseDTO(
                        saved.getId().toString(),
                        saved.getPattern(),
                        saved.getCategory(),
                        saved.getPriority(),
                        saved.getCreatedAt()
                ),
                "Regra criada"
        ));
    }

    private User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new IllegalStateException("Usuário não autenticado");
        }
        return userService.findByEmail(auth.getName());
    }
}
