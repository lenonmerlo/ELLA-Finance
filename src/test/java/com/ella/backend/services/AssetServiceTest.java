package com.ella.backend.services;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ella.backend.dto.asset.AssetRequest;
import com.ella.backend.entities.Asset;
import com.ella.backend.entities.Investment;
import com.ella.backend.entities.Person;
import com.ella.backend.enums.AssetType;
import com.ella.backend.exceptions.BadRequestException;
import com.ella.backend.repositories.AssetRepository;
import com.ella.backend.repositories.InvestmentRepository;
import com.ella.backend.repositories.PersonRepository;

@ExtendWith(MockitoExtension.class)
class AssetServiceTest {

    @Mock
    private AssetRepository assetRepository;

    @Mock
    private PersonRepository personRepository;

    @Mock
    private InvestmentRepository investmentRepository;

    @Mock
    private AssetSyncService assetSyncService;

    @InjectMocks
    private AssetService assetService;

    @Test
    void update_syncedAsset_throwsBadRequest() {
        UUID assetId = UUID.randomUUID();
        Asset asset = Asset.builder()
                .id(assetId)
                .name("Sync")
                .type(AssetType.INVESTIMENTO)
                .currentValue(new BigDecimal("10.00"))
                .syncedFromInvestment(true)
                .investmentId(UUID.randomUUID())
                .build();

        when(assetRepository.findById(assetId)).thenReturn(Optional.of(asset));

        AssetRequest req = new AssetRequest();
        req.setName("Novo");
        req.setType(AssetType.OUTROS);
        req.setCurrentValue(new BigDecimal("10.00"));
        req.setPurchaseValue(new BigDecimal("5.00"));
        req.setPurchaseDate(LocalDate.of(2025, 1, 1));

        assertThrows(BadRequestException.class, () -> assetService.update(assetId.toString(), req));
    }

    @Test
    void delete_syncedAsset_marksInvestmentExcluded() {
        UUID assetId = UUID.randomUUID();
        UUID investmentId = UUID.randomUUID();

        Person owner = new Person();
        owner.setId(UUID.randomUUID());

        Asset asset = Asset.builder()
                .id(assetId)
                .owner(owner)
                .name("Sync")
                .type(AssetType.INVESTIMENTO)
                .currentValue(new BigDecimal("10.00"))
                .syncedFromInvestment(true)
                .investmentId(investmentId)
                .build();

        Investment inv = new Investment();
        inv.setId(investmentId);
        inv.setExcludedFromAssets(false);

        when(assetRepository.findById(assetId)).thenReturn(Optional.of(asset));
        when(investmentRepository.findById(investmentId)).thenReturn(Optional.of(inv));

        assetService.delete(assetId.toString());

        verify(investmentRepository).save(inv);
        verify(assetRepository).delete(asset);
    }
}
