package com.ella.backend.controllers;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ella.backend.dto.asset.AssetRequest;
import com.ella.backend.dto.asset.AssetResponse;
import com.ella.backend.dto.asset.AssetTotalValueResponse;
import com.ella.backend.services.AssetService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/assets")
@RequiredArgsConstructor
public class AssetController {

    private final AssetService assetService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or @securityService.canAccessPerson(#personId)")
    public ResponseEntity<AssetResponse> create(
            @RequestParam String personId,
            @Valid @RequestBody AssetRequest request
    ) {
        AssetResponse created = assetService.create(personId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/person/{personId}")
    @PreAuthorize("hasRole('ADMIN') or @securityService.canAccessAssetsOfPerson(#personId)")
    public ResponseEntity<List<AssetResponse>> getByPerson(
            @PathVariable String personId
    ) {
        return ResponseEntity.ok(assetService.getByPerson(personId));
    }

    @GetMapping("/{assetId}")
    @PreAuthorize("hasRole('ADMIN') or @securityService.canAccessAsset(#assetId)")
    public ResponseEntity<AssetResponse> findById(
            @PathVariable String assetId
    ) {
        return ResponseEntity.ok(assetService.findById(assetId));
    }

    @PutMapping("/{assetId}")
    @PreAuthorize("hasRole('ADMIN') or @securityService.canAccessAsset(#assetId)")
    public ResponseEntity<AssetResponse> update(
            @PathVariable String assetId,
            @Valid @RequestBody AssetRequest request
    ) {
        return ResponseEntity.ok(assetService.update(assetId, request));
    }

    @DeleteMapping("/{assetId}")
    @PreAuthorize("hasRole('ADMIN') or @securityService.canAccessAsset(#assetId)")
    public ResponseEntity<Void> delete(
            @PathVariable String assetId
    ) {
        assetService.delete(assetId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/total-value")
    @PreAuthorize("hasRole('ADMIN') or @securityService.canAccessAssetsOfPerson(#personId)")
    public ResponseEntity<AssetTotalValueResponse> totalValue(
            @RequestParam String personId
    ) {
        return ResponseEntity.ok(assetService.getTotalValue(personId));
    }
}
