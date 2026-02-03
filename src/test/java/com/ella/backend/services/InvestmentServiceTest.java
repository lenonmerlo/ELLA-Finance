package com.ella.backend.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ella.backend.dto.investment.InvestmentRequest;
import com.ella.backend.dto.investment.InvestmentResponse;
import com.ella.backend.dto.investment.InvestmentSummaryResponse;
import com.ella.backend.entities.Investment;
import com.ella.backend.entities.Person;
import com.ella.backend.enums.InvestmentType;
import com.ella.backend.exceptions.BadRequestException;
import com.ella.backend.repositories.InvestmentRepository;
import com.ella.backend.repositories.PersonRepository;

@ExtendWith(MockitoExtension.class)
class InvestmentServiceTest {

    @Mock
    private InvestmentRepository investmentRepository;

    @Mock
    private PersonRepository personRepository;

    @Mock
    private AssetSyncService assetSyncService;

    @InjectMocks
    private InvestmentService investmentService;

    @Test
    void create_validValues_calculatesProfitability() {
        UUID personId = UUID.randomUUID();
        Person person = new Person();
        person.setId(personId);
        when(personRepository.findById(personId)).thenReturn(Optional.of(person));

        when(investmentRepository.save(any(Investment.class))).thenAnswer(inv -> {
            Investment i = inv.getArgument(0);
            if (i.getId() == null) i.setId(UUID.randomUUID());
            i.setCreatedAt(LocalDateTime.now());
            i.setUpdatedAt(LocalDateTime.now());
            return i;
        });

        InvestmentRequest req = request("Tesouro Selic", InvestmentType.FIXED_INCOME, "1000", "1100");
        InvestmentResponse resp = investmentService.create(personId.toString(), req);

        assertNotNull(resp.getId());
        assertEquals(new BigDecimal("10.00"), resp.getProfitability());
        verify(investmentRepository).save(any(Investment.class));
        verify(assetSyncService).upsertFromInvestment(any(Investment.class));
    }

    @Test
    void create_invalidInitialValue_throwsBadRequest() {
        UUID personId = UUID.randomUUID();
        Person person = new Person();
        person.setId(personId);
        when(personRepository.findById(personId)).thenReturn(Optional.of(person));

        InvestmentRequest req = request("Algo", InvestmentType.OTHER, "0", "10");
        assertThrows(BadRequestException.class, () -> investmentService.create(personId.toString(), req));
    }

    @Test
    void update_recalculatesProfitability() {
        UUID investmentId = UUID.randomUUID();

        Investment existing = Investment.builder()
                .id(investmentId)
                .name("Bitcoin")
                .type(InvestmentType.CRYPTOCURRENCY)
                .initialValue(new BigDecimal("100.00"))
                .currentValue(new BigDecimal("100.00"))
                .investmentDate(LocalDate.now())
                .profitability(new BigDecimal("0.00"))
                .build();

        when(investmentRepository.findById(investmentId)).thenReturn(Optional.of(existing));
        when(investmentRepository.save(any(Investment.class))).thenAnswer(inv -> inv.getArgument(0));

        InvestmentRequest req = request("Bitcoin", InvestmentType.CRYPTOCURRENCY, "100", "80");
        InvestmentResponse resp = investmentService.update(investmentId.toString(), req);

        assertEquals(new BigDecimal("-20.00"), resp.getProfitability());
        verify(investmentRepository).save(any(Investment.class));
        verify(assetSyncService).upsertFromInvestment(any(Investment.class));
    }

    @Test
    void getByPerson_returnsSummaryTotals() {
        UUID personId = UUID.randomUUID();
        Person person = new Person();
        person.setId(personId);

        when(personRepository.findById(personId)).thenReturn(Optional.of(person));

        Investment i1 = Investment.builder()
                .id(UUID.randomUUID())
                .owner(person)
                .name("A")
                .type(InvestmentType.SAVINGS)
                .initialValue(new BigDecimal("100.00"))
                .currentValue(new BigDecimal("110.00"))
                .investmentDate(LocalDate.now())
                .profitability(new BigDecimal("10.00"))
                .build();

        Investment i2 = Investment.builder()
                .id(UUID.randomUUID())
                .owner(person)
                .name("B")
                .type(InvestmentType.FIXED_INCOME)
                .initialValue(new BigDecimal("200.00"))
                .currentValue(new BigDecimal("190.00"))
                .investmentDate(LocalDate.now())
                .profitability(new BigDecimal("-5.00"))
                .build();

        when(investmentRepository.findByOwner(person)).thenReturn(List.of(i1, i2));

        InvestmentSummaryResponse summary = investmentService.getByPerson(personId.toString());

        assertEquals(new BigDecimal("300.00"), summary.getTotalInvested());
        assertEquals(new BigDecimal("300.00"), summary.getTotalCurrent());
        assertEquals(new BigDecimal("0.00"), summary.getTotalProfitability());
        assertEquals(2, summary.getInvestments().size());
    }

    private InvestmentRequest request(String name, InvestmentType type, String initial, String current) {
        InvestmentRequest req = new InvestmentRequest();
        req.setName(name);
        req.setType(type);
        req.setInitialValue(new BigDecimal(initial));
        req.setCurrentValue(new BigDecimal(current));
        req.setInvestmentDate(LocalDate.of(2025, 1, 1));
        req.setDescription("teste");
        return req;
    }
}
