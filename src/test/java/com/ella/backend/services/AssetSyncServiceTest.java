package com.ella.backend.services;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ella.backend.entities.Asset;
import com.ella.backend.entities.Investment;
import com.ella.backend.entities.Person;
import com.ella.backend.enums.InvestmentType;
import com.ella.backend.repositories.AssetRepository;
import com.ella.backend.repositories.InvestmentRepository;

@ExtendWith(MockitoExtension.class)
class AssetSyncServiceTest {

    @Mock
    private AssetRepository assetRepository;

    @Mock
    private InvestmentRepository investmentRepository;

    @InjectMocks
    private AssetSyncService assetSyncService;

    @Test
    void upsertFromInvestment_whenNotExcluded_createsOrUpdatesAsset() {
        Person person = new Person();
        person.setId(UUID.randomUUID());

        UUID investmentId = UUID.randomUUID();
        Investment inv = Investment.builder()
                .id(investmentId)
                .owner(person)
                .name("Tesouro")
                .type(InvestmentType.FIXED_INCOME)
                .initialValue(new BigDecimal("100.00"))
                .currentValue(new BigDecimal("110.00"))
                .investmentDate(LocalDate.of(2025, 1, 1))
                .excludedFromAssets(false)
                .build();

        when(assetRepository.findByInvestmentId(investmentId)).thenReturn(Optional.empty());
        when(assetRepository.save(any(Asset.class))).thenAnswer(a -> a.getArgument(0));

        assetSyncService.upsertFromInvestment(inv);

        verify(assetRepository).save(any(Asset.class));
    }

    @Test
    void upsertFromInvestment_whenExcluded_doesNotPersistAsset() {
        Person person = new Person();
        person.setId(UUID.randomUUID());

        Investment inv = Investment.builder()
                .id(UUID.randomUUID())
                .owner(person)
                .name("Fundo")
                .type(InvestmentType.VARIABLE_INCOME)
                .initialValue(new BigDecimal("100.00"))
                .currentValue(new BigDecimal("120.00"))
                .investmentDate(LocalDate.of(2025, 1, 1))
                .excludedFromAssets(true)
                .build();

        assetSyncService.upsertFromInvestment(inv);

        verify(assetRepository, never()).save(any(Asset.class));
    }

    @Test
    void syncForPerson_ignoresExcludedInvestments() {
        Person person = new Person();
        person.setId(UUID.randomUUID());

        Investment excluded = Investment.builder()
                .id(UUID.randomUUID())
                .owner(person)
                .name("Cripto")
                .type(InvestmentType.CRYPTOCURRENCY)
                .initialValue(new BigDecimal("100.00"))
                .currentValue(new BigDecimal("80.00"))
                .investmentDate(LocalDate.of(2025, 1, 1))
                .excludedFromAssets(true)
                .build();

        when(investmentRepository.findByOwnerAndExcludedFromAssetsFalse(person)).thenReturn(List.of());
        when(assetRepository.findByOwner(person)).thenReturn(List.of());

        assetSyncService.syncForPerson(person);

        verify(assetRepository, never()).save(any(Asset.class));
    }
}
