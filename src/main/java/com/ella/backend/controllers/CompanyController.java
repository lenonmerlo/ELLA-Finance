package com.ella.backend.controllers;

import com.ella.backend.dto.ApiResponse;
import com.ella.backend.dto.CompanyRequestDTO;
import com.ella.backend.dto.CompanyResponseDTO;
import com.ella.backend.services.CompanyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/companies")
@RequiredArgsConstructor
public class CompanyController {

    private final CompanyService companyService;

    @PostMapping
    public ResponseEntity<ApiResponse<CompanyResponseDTO>> create(@Valid @RequestBody CompanyRequestDTO dto) {
        CompanyResponseDTO created = companyService.create(dto);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(created, "Empresa criada com sucesso"));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<CompanyResponseDTO>>> findAll() {
        return ResponseEntity.ok(ApiResponse.success(
                companyService.findAll(), "Empresas encontradas"
        ));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<CompanyResponseDTO>> findById(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.success(
                companyService.findById(id), "Empresa encontrada"
        ));
    }

    @GetMapping("/owner/{ownerId}")
    public ResponseEntity<ApiResponse<List<CompanyResponseDTO>>> findByOwner(@PathVariable String ownerId) {
        return ResponseEntity.ok(ApiResponse.success(companyService.findByOwner(ownerId), "Empresas do owner"));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<CompanyResponseDTO>> update(
            @PathVariable String id,
            @Valid
            @RequestBody CompanyRequestDTO dto
    ) {
        return ResponseEntity.ok(ApiResponse.success(companyService.update(id, dto), "Empresa atualizada"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Valid>> delete(@PathVariable String id) {
        companyService.delete(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Empresa deletada"));
    }

}
