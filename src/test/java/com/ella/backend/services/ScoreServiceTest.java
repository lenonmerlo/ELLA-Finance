package com.ella.backend.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ella.backend.entities.CreditCard;
import com.ella.backend.entities.FinancialTransaction;
import com.ella.backend.entities.Invoice;
import com.ella.backend.entities.Person;
import com.ella.backend.entities.Score;
import com.ella.backend.enums.InvoiceStatus;
import com.ella.backend.enums.TransactionStatus;
import com.ella.backend.enums.TransactionType;
import com.ella.backend.repositories.CreditCardRepository;
import com.ella.backend.repositories.FinancialTransactionRepository;
import com.ella.backend.repositories.InvoiceRepository;
import com.ella.backend.repositories.PersonRepository;
import com.ella.backend.repositories.ScoreRepository;

@ExtendWith(MockitoExtension.class)
class ScoreServiceTest {

    @Mock
    private ScoreRepository scoreRepository;

    @Mock
    private InvoiceRepository invoiceRepository;

    @Mock
    private FinancialTransactionRepository transactionRepository;

    @Mock
    private PersonRepository personRepository;

    @Mock
    private CreditCardRepository creditCardRepository;

    @InjectMocks
    private ScoreService scoreService;

    @Test
    void calculateScore_savesScoreWithComponents() {
        UUID personId = UUID.randomUUID();
        Person person = new Person();
        person.setId(personId);

        when(personRepository.findById(personId)).thenReturn(Optional.of(person));

        CreditCard card = new CreditCard();
        card.setLimitAmount(new BigDecimal("10000"));
        when(creditCardRepository.findByOwner(person)).thenReturn(List.of(card));

        Invoice latest = new Invoice();
        latest.setMonth(12);
        latest.setYear(2025);
        latest.setDueDate(LocalDate.now().minusDays(10));
        when(invoiceRepository.findTopByCardOwnerAndDeletedAtIsNullOrderByYearDescMonthDesc(person)).thenReturn(Optional.of(latest));

        Invoice inv = new Invoice();
        inv.setTotalAmount(new BigDecimal("7000"));
        inv.setDueDate(LocalDate.now().minusDays(10));
        inv.setStatus(InvoiceStatus.PAID);
        inv.setPaidDate(inv.getDueDate());
        when(invoiceRepository.findByCardOwnerAndMonthAndYearAndDeletedAtIsNull(person, 12, 2025)).thenReturn(List.of(inv));

        when(invoiceRepository.findByCardOwnerAndDeletedAtIsNull(person)).thenReturn(List.of(inv));
        when(invoiceRepository.findTopByCardOwnerAndDeletedAtIsNullOrderByDueDateAsc(person)).thenReturn(Optional.of(inv));

        FinancialTransaction tx1 = new FinancialTransaction();
        tx1.setType(TransactionType.EXPENSE);
        tx1.setStatus(TransactionStatus.PAID);
        tx1.setCategory("Alimentação");
        tx1.setTransactionDate(LocalDate.now().minusDays(5));
        tx1.setAmount(new BigDecimal("100"));

        when(transactionRepository.findByPersonAndTransactionDateBetweenAndDeletedAtIsNull(any(), any(), any()))
                .thenReturn(List.of(tx1));

        when(scoreRepository.save(any(Score.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Score result = scoreService.calculateScore(personId);

        assertNotNull(result);
        assertEquals(person, result.getPerson());
        assertNotNull(result.getCalculationDate());

        ArgumentCaptor<Score> captor = ArgumentCaptor.forClass(Score.class);
        verify(scoreRepository).save(captor.capture());

        Score saved = captor.getValue();
        assertEquals(70, saved.getCreditUtilizationScore());
        assertEquals(100, saved.getOnTimePaymentScore());
        assertEquals(20, saved.getSpendingDiversityScore());
        assertNotNull(saved.getScoreValue());
    }
}
