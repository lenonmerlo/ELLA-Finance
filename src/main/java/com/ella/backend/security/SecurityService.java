package com.ella.backend.security;

import com.ella.backend.entities.*;
import com.ella.backend.enums.Role;
import com.ella.backend.repositories.*;
import com.ella.backend.services.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component("securityService")
@RequiredArgsConstructor
public class SecurityService {

    private final UserService userService;

    private final PersonRepository personRepository;
    private final CompanyRepository companyRepository;
    private final FinancialTransactionRepository financialTransactionRepository;
    private final CreditCardRepository creditCardRepository;
    private final InvoiceRepository invoiceRepository;
    private final GoalRepository goalRepository;

    // =========================
    // Helpers centrais
    // =========================

    private Optional<User> getCurrentUserOptional() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth == null || !auth.isAuthenticated()) {
            return Optional.empty();
        }

        String email = auth.getName(); // subject do JWT
        try {
            User user = userService.findByEmail(email);
            return Optional.of(user);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private User getCurrentUserOrThrow() {
        return getCurrentUserOptional()
                .orElseThrow(() -> new IllegalStateException("Usuário autenticado não encontrado no banco"));
    }

    private UUID parseUuid(String id) {
        return UUID.fromString(id);
    }

    private boolean isAdmin(User user) {
        return user.getRole() == Role.ADMIN;
    }

    public String getCurrentUserId() {
        return getCurrentUserOrThrow().getId().toString();
    }

    // =========================
    // Regras genéricas
    // =========================

    /**
     * Verifica se o usuário logado é o próprio dono (User/Person) do id informado.
     * Pode ser usado em endpoints de /api/users/{id} ou /api/persons/{id}.
     */
    public boolean isCurrentUser(String id) {
        User current = getCurrentUserOrThrow();
        UUID targetId = parseUuid(id);
        return current.getId().equals(targetId);
    }

    // =========================
    // Person
    // =========================

    public boolean canAccessPerson(String personId) {
        User current = getCurrentUserOrThrow();
        if (isAdmin(current)) return true;

        UUID id = parseUuid(personId);

        return personRepository.findById(id)
                .map(person -> person.getId().equals(current.getId()))
                .orElse(false);
    }

    // =========================
    // Company
    // =========================

    public boolean canAccessCompany(String companyId) {
        User current = getCurrentUserOrThrow();
        if (isAdmin(current)) return true;

        UUID id = parseUuid(companyId);

        return companyRepository.findById(id)
                .map(company -> company.getOwner() != null
                        && company.getOwner().getId().equals(current.getId()))
                .orElse(false);
    }

    // =========================
    // FinancialTransaction
    // =========================

    public boolean canAccessTransaction(String transactionId) {
        User current = getCurrentUserOrThrow();
        if (isAdmin(current)) return true;

        UUID id = parseUuid(transactionId);

        return financialTransactionRepository.findById(id)
                .map(tx -> tx.getPerson() != null
                        && tx.getPerson().getId().equals(current.getId()))
                .orElse(false);
    }

    // Para endpoints que usam personId direto (ex: /transactions/person/{personId})
    public boolean canAccessTransactionsOfPerson(String personId) {
        return canAccessPerson(personId);
    }

    // =========================
    // CreditCard
    // =========================

    public boolean canAccessCreditCard(String cardId) {
        User current = getCurrentUserOrThrow();
        if (isAdmin(current)) return true;

        UUID id = parseUuid(cardId);

        return creditCardRepository.findById(id)
                .map(card -> card.getOwner() != null
                        && card.getOwner().getId().equals(current.getId()))
                .orElse(false);
    }

    // =========================
    // Invoice
    // =========================

    public boolean canAccessInvoice(String invoiceId) {
        User current = getCurrentUserOrThrow();
        if (isAdmin(current)) return true;

        UUID id = parseUuid(invoiceId);

        return invoiceRepository.findById(id)
                .map(inv -> inv.getCard() != null
                        && inv.getCard().getOwner() != null
                        && inv.getCard().getOwner().getId().equals(current.getId()))
                .orElse(false);
    }

    // =========================
    // Goal
    // =========================

    public boolean canAccessGoal(String goalId) {
        User current = getCurrentUserOrThrow();
        if (isAdmin(current)) return true;

        UUID id = parseUuid(goalId);

        return goalRepository.findById(id)
                .map(goal -> goal.getOwner() != null
                        && goal.getOwner().getId().equals(current.getId()))
                .orElse(false);
    }
}
