package com.ella.backend.services;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.ella.backend.entities.Asset;
import com.ella.backend.entities.Investment;
import com.ella.backend.entities.Person;
import com.ella.backend.enums.AssetType;
import com.ella.backend.repositories.AssetRepository;
import com.ella.backend.repositories.InvestmentRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AssetSyncService {

    private static final int MONEY_SCALE = 2;

    private final AssetRepository assetRepository;
    private final InvestmentRepository investmentRepository;

    public void syncForPerson(Person person) {
        List<Investment> investments = investmentRepository.findByOwnerAndExcludedFromAssetsFalse(person);
        Set<UUID> investmentIds = new HashSet<>();

        for (Investment inv : investments) {
            if (inv.getId() == null) continue;
            investmentIds.add(inv.getId());
            upsertFromInvestment(inv);
        }

        // Cleanup: remove synced assets that no longer have a backing investment
        List<Asset> existingAssets = assetRepository.findByOwner(person);
        for (Asset asset : existingAssets) {
            if (!asset.isSyncedFromInvestment()) continue;
            UUID invId = asset.getInvestmentId();
            if (invId != null && !investmentIds.contains(invId)) {
                assetRepository.delete(asset);
            }
        }
    }

    public void upsertFromInvestment(Investment investment) {
        if (investment == null || investment.getId() == null) return;
        if (investment.isExcludedFromAssets()) return;

        Asset asset = assetRepository.findByInvestmentId(investment.getId())
                .orElseGet(Asset::new);

        asset.setOwner(investment.getOwner());
        asset.setName(investment.getName());
        asset.setType(AssetType.INVESTIMENTO);
        asset.setPurchaseValue(moneyOrNull(investment.getInitialValue()));
        asset.setCurrentValue(money(investment.getCurrentValue()));
        asset.setPurchaseDate(investment.getInvestmentDate());
        asset.setSyncedFromInvestment(true);
        asset.setInvestmentId(investment.getId());

        assetRepository.save(asset);
    }

    public void deleteForInvestment(UUID investmentId) {
        if (investmentId == null) return;
        assetRepository.deleteByInvestmentId(investmentId);
    }

    private BigDecimal money(BigDecimal value) {
        if (value == null) return BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        return value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal moneyOrNull(BigDecimal value) {
        return value == null ? null : value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }
}
