package com.ella.backend.services;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.ella.backend.dto.asset.AssetRequest;
import com.ella.backend.dto.asset.AssetResponse;
import com.ella.backend.dto.asset.AssetTotalValueResponse;
import com.ella.backend.entities.Asset;
import com.ella.backend.entities.Person;
import com.ella.backend.exceptions.BadRequestException;
import com.ella.backend.exceptions.ResourceNotFoundException;
import com.ella.backend.repositories.AssetRepository;
import com.ella.backend.repositories.InvestmentRepository;
import com.ella.backend.repositories.PersonRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AssetService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final int MONEY_SCALE = 2;

    private final AssetRepository assetRepository;
    private final PersonRepository personRepository;
    private final InvestmentRepository investmentRepository;
    private final AssetSyncService assetSyncService;

    public AssetResponse create(String personId, AssetRequest request) {
        Person person = findPersonOrThrow(personId);
        validateRequest(request);

        Asset asset = new Asset();
        asset.setOwner(person);
        applyRequest(asset, request);
        asset.setSyncedFromInvestment(false);
        asset.setInvestmentId(null);

        Asset saved = assetRepository.save(asset);
        return toResponse(saved);
    }

    public List<AssetResponse> getByPerson(String personId) {
        Person person = findPersonOrThrow(personId);
        assetSyncService.syncForPerson(person);
        return assetRepository.findByOwner(person).stream().map(this::toResponse).toList();
    }

    public AssetResponse findById(String assetId) {
        UUID uuid = parseUuid(assetId, "assetId");
        Asset asset = assetRepository.findById(uuid)
                .orElseThrow(() -> new ResourceNotFoundException("Ativo não encontrado"));
        return toResponse(asset);
    }

    public AssetResponse update(String assetId, AssetRequest request) {
        UUID uuid = parseUuid(assetId, "assetId");
        Asset asset = assetRepository.findById(uuid)
                .orElseThrow(() -> new ResourceNotFoundException("Ativo não encontrado"));

        if (asset.isSyncedFromInvestment()) {
            throw new BadRequestException("Ativo sincronizado não pode ser editado diretamente");
        }

        validateRequest(request);
        applyRequest(asset, request);

        Asset saved = assetRepository.save(asset);
        return toResponse(saved);
    }

    public void delete(String assetId) {
        UUID uuid = parseUuid(assetId, "assetId");
        Asset asset = assetRepository.findById(uuid)
                .orElseThrow(() -> new ResourceNotFoundException("Ativo não encontrado"));

        if (asset.isSyncedFromInvestment() && asset.getInvestmentId() != null) {
            investmentRepository.findById(asset.getInvestmentId()).ifPresent(inv -> {
                inv.setExcludedFromAssets(true);
                investmentRepository.save(inv);
            });
        }

        assetRepository.delete(asset);
    }

    public AssetTotalValueResponse getTotalValue(String personId) {
        Person person = findPersonOrThrow(personId);
        assetSyncService.syncForPerson(person);

        BigDecimal total = assetRepository.findByOwner(person).stream()
                .map(Asset::getCurrentValue)
                .filter(v -> v != null)
                .reduce(ZERO, BigDecimal::add)
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP);

        return new AssetTotalValueResponse(total);
    }

    private void validateRequest(AssetRequest request) {
        if (request == null) throw new BadRequestException("Dados do ativo são obrigatórios");
        if (request.getName() == null || request.getName().isBlank()) throw new BadRequestException("Nome é obrigatório");
        if (request.getType() == null) throw new BadRequestException("Tipo é obrigatório");

        if (request.getCurrentValue() == null) throw new BadRequestException("Valor atual é obrigatório");
        if (request.getCurrentValue().compareTo(ZERO) < 0) throw new BadRequestException("Valor atual não pode ser negativo");

        if (request.getPurchaseValue() != null && request.getPurchaseValue().compareTo(ZERO) < 0) {
            throw new BadRequestException("Valor de compra não pode ser negativo");
        }
    }

    private void applyRequest(Asset asset, AssetRequest request) {
        asset.setName(request.getName().trim());
        asset.setType(request.getType());
        asset.setPurchaseValue(moneyOrNull(request.getPurchaseValue()));
        asset.setCurrentValue(money(request.getCurrentValue()));
        asset.setPurchaseDate(request.getPurchaseDate());
    }

    private BigDecimal money(BigDecimal value) {
        return value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal moneyOrNull(BigDecimal value) {
        return value == null ? null : value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private Person findPersonOrThrow(String personId) {
        UUID uuid = parseUuid(personId, "personId");
        return personRepository.findById(uuid)
                .orElseThrow(() -> new ResourceNotFoundException("Pessoa não encontrada"));
    }

    private UUID parseUuid(String raw, String fieldName) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(fieldName + " inválido");
        }
    }

    private AssetResponse toResponse(Asset asset) {
        AssetResponse response = new AssetResponse();
        response.setId(asset.getId());
        response.setName(asset.getName());
        response.setType(asset.getType());
        response.setPurchaseValue(asset.getPurchaseValue());
        response.setCurrentValue(asset.getCurrentValue());
        response.setPurchaseDate(asset.getPurchaseDate());
        response.setSyncedFromInvestment(asset.isSyncedFromInvestment());
        response.setInvestmentId(asset.getInvestmentId());
        response.setCreatedAt(asset.getCreatedAt());
        response.setUpdatedAt(asset.getUpdatedAt());
        return response;
    }
}
