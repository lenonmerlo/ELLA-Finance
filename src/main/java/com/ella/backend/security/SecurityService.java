package com.ella.backend.security;

import java.util.UUID;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import com.ella.backend.entities.User;
import com.ella.backend.enums.Role;
import com.ella.backend.exceptions.ResourceNotFoundException;
import com.ella.backend.repositories.AssetRepository;
import com.ella.backend.repositories.BudgetRepository;
import com.ella.backend.repositories.CompanyRepository;
import com.ella.backend.repositories.CreditCardRepository;
import com.ella.backend.repositories.ExpenseRepository;
import com.ella.backend.repositories.FinancialTransactionRepository;
import com.ella.backend.repositories.GoalRepository;
import com.ella.backend.repositories.InvestmentRepository;
import com.ella.backend.repositories.InvoiceRepository;
import com.ella.backend.repositories.PersonRepository;
import com.ella.backend.services.UserService;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class SecurityService {

    private final UserService userService;
    private final PersonRepository personRepository;
    private final FinancialTransactionRepository financialTransactionRepository;
    private final CompanyRepository companyRepository;
    private final CreditCardRepository creditCardRepository;
    private final InvoiceRepository invoiceRepository;
    private final GoalRepository goalRepository;
    private final ExpenseRepository expenseRepository;
    private final BudgetRepository budgetRepository;
    private final InvestmentRepository investmentRepository;
    private final AssetRepository assetRepository;

    // =========================================================
    // Helpers centrais
    // =========================================================

    /** Busca o usuário autenticado ou lança AccessDeniedException. */
    private User getAuthenticatedUserOrThrow() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth == null || !auth.isAuthenticated()
                || "anonymousUser".equals(auth.getPrincipal())) {
            throw new InsufficientAuthenticationException("Usuário não autenticado");
        }

        String email = auth.getName(); // subject do JWT
        try {
            return userService.findByEmail(email);
        } catch (ResourceNotFoundException e) {
            // Não vaze informação sobre existência de usuário; trate como acesso negado.
            throw new AccessDeniedException("Usuário não autorizado");
        }
    }

    /** Verifica se o usuário é ADMIN. */
    private boolean isAdmin(User user) {
        return user.getRole() == Role.ADMIN;
    }

    // =========================================================
    // Regras genéricas
    // =========================================================

    /** Versão antiga, se ainda tiver algum @PreAuthorize usando. */
    public boolean isCurrentUser(String id) {
        User current = getAuthenticatedUserOrThrow();
        System.out.println("[SecurityService.isCurrentUser] Comparing: current.id=" + current.getId() + " vs provided id=" + id);
        boolean result = current.getId() != null
                && current.getId().toString().equals(id);
        System.out.println("[SecurityService.isCurrentUser] Result: " + result);
        return result;
    }

    // =========================================================
    // Person
    // =========================================================

    public boolean canAccessPerson(String personId) {
        User current = getAuthenticatedUserOrThrow();

        // ADMIN pode tudo
        if (isAdmin(current)) return true;

        // USER só pode ver/alterar a própria Person
        try {
            UUID uuid = UUID.fromString(personId);
            return personRepository.findById(uuid)
                    .map(person -> person.getId() != null
                            && person.getId().equals(current.getId()))
                    .orElse(false);
        } catch (IllegalArgumentException e) {
            // id inválido = nega acesso
            return false;
        }
    }

    // =========================================================
    // FinancialTransaction
    // =========================================================

    public boolean canAccessTransaction(String transactionId) {
        User user = getAuthenticatedUserOrThrow();
        if (isAdmin(user)) return true;

        try {
            UUID uuid = UUID.fromString(transactionId);
            return financialTransactionRepository.findByIdAndDeletedAtIsNull(uuid)
                    .map(tx ->
                            tx.getPerson() != null &&
                                    tx.getPerson().getId().equals(user.getId())
                    )
                    .orElse(false);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public boolean canAccessTransactionsOfPerson(String personId) {
        // Mesma regra de Person
        return canAccessPerson(personId);
    }

    // =========================================================
    // Company
    // =========================================================

    public boolean canAccessCompany(String companyId) {
        User user = getAuthenticatedUserOrThrow();
        if (isAdmin(user)) return true;

        try {
            UUID uuid = UUID.fromString(companyId);
            return companyRepository.findById(uuid)
                    .map(company ->
                            company.getOwner() != null &&
                                    company.getOwner().getId().equals(user.getId())
                    )
                    .orElse(false);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    // =========================================================
    // CreditCard
    // =========================================================

    /**
     * Alias para compatibilidade com anotações existentes.
     * Alguns controllers usam canAccessCard(...).
     */
    public boolean canAccessCard(String cardId) {
        return canAccessCreditCard(cardId);
    }

    public boolean canAccessCreditCard(String cardId) {
        User user = getAuthenticatedUserOrThrow();
        if (isAdmin(user)) return true;

        try {
            UUID uuid = UUID.fromString(cardId);
            return creditCardRepository.findById(uuid)
                    .map(card ->
                            card.getOwner() != null &&
                                    card.getOwner().getId().equals(user.getId())
                    )
                    .orElse(false);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    // =========================================================
    // Invoice
    // =========================================================

    public boolean canAccessInvoice(String invoiceId) {
        User user = getAuthenticatedUserOrThrow();
        if (isAdmin(user)) return true;

        try {
            UUID uuid = UUID.fromString(invoiceId);
            return invoiceRepository.findByIdAndDeletedAtIsNull(uuid)
                    .map(inv -> inv.getCard() != null
                            && inv.getCard().getOwner() != null
                            && inv.getCard().getOwner().getId().equals(user.getId()))
                    .orElse(false);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    // =========================================================
    // Goal
    // =========================================================

    public boolean canAccessGoal(String goalId) {
        User user = getAuthenticatedUserOrThrow();
        if (isAdmin(user)) return true;

        try {
            UUID uuid = UUID.fromString(goalId);
            return goalRepository.findById(uuid)
                    .map(goal ->
                            goal.getOwner() != null &&
                                    goal.getOwner().getId().equals(user.getId())
                    )
                    .orElse(false);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    // =========================================================
    // Budget
    // =========================================================

    public boolean canAccessBudget(String budgetId) {
        User user = getAuthenticatedUserOrThrow();
        if (isAdmin(user)) return true;

        try {
            UUID uuid = UUID.fromString(budgetId);
            return budgetRepository.findById(uuid)
                    .map(budget ->
                            budget.getOwner() != null &&
                                    budget.getOwner().getId().equals(user.getId())
                    )
                    .orElse(false);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    // =========================================================
    // Investment
    // =========================================================

    public boolean canAccessInvestment(String investmentId) {
        User user = getAuthenticatedUserOrThrow();
        if (isAdmin(user)) return true;

        try {
            UUID uuid = UUID.fromString(investmentId);
            return investmentRepository.findById(uuid)
                    .map(investment ->
                            investment.getOwner() != null &&
                                    investment.getOwner().getId().equals(user.getId())
                    )
                    .orElse(false);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public boolean canAccessInvestmentsOfPerson(String personId) {
        return canAccessPerson(personId);
    }

    // =========================================================
    // Asset
    // =========================================================

    public boolean canAccessAsset(String assetId) {
        User user = getAuthenticatedUserOrThrow();
        if (isAdmin(user)) return true;

        try {
            UUID uuid = UUID.fromString(assetId);
            return assetRepository.findById(uuid)
                    .map(asset ->
                            asset.getOwner() != null &&
                                    asset.getOwner().getId().equals(user.getId())
                    )
                    .orElse(false);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public boolean canAccessAssetsOfPerson(String personId) {
        return canAccessPerson(personId);
    }

    // =========================================================
    // Expense
    // =========================================================

    public boolean canAccessExpense(String expenseId) {
        User user = getAuthenticatedUserOrThrow();
        if (isAdmin(user)) return true;

        try {
            UUID uuid = UUID.fromString(expenseId);
            return expenseRepository.findById(uuid)
                    .map(expense ->
                            expense.getPerson() != null &&
                                    expense.getPerson().getId().equals(user.getId())
                    )
                    .orElse(false);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}

