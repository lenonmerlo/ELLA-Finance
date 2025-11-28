package com.ella.backend.controllers;

import com.ella.backend.dto.ApiResponse;
import com.ella.backend.dto.CompanyRequestDTO;
import com.ella.backend.dto.CompanyResponseDTO;
import com.ella.backend.services.CompanyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/companies")
@RequiredArgsConstructor
public class CompanyController {

    private final CompanyService companyService;

    /**
     * Criar empresa.
     * Regra:
     *  - ADMIN pode criar para qualquer owner
     *  - USER só pode criar empresa para ele mesmo (ownerId dele)
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or @securityService.canAccessPerson(#dto.ownerId)")
    public ResponseEntity<ApiResponse<CompanyResponseDTO>> create(@Valid @RequestBody CompanyRequestDTO dto) {
        CompanyResponseDTO created = companyService.create(dto);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(created, "Empresa criada com sucesso"));
    }

    /**
     * Listar todas as empresas.
     * Regra:
     *  - Apenas ADMIN (isso é dado sensível / visão global)
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<CompanyResponseDTO>>> findAll() {
        return ResponseEntity.ok(ApiResponse.success(
                companyService.findAll(), "Empresas encontradas"
        ));
    }

    /**
     * Buscar empresa por ID.
     * Regra:
     *  - ADMIN pode tudo
     *  - USER só se for dono da empresa
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or @securityService.canAccessCompany(#id)")
    public ResponseEntity<ApiResponse<CompanyResponseDTO>> findById(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.success(
                companyService.findById(id), "Empresa encontrada"
        ));
    }

    /**
     * Buscar empresas de um owner específico.
     * Regra:
     *  - ADMIN pode ver de qualquer owner
     *  - USER só pode ver se for o próprio owner
     */
    @GetMapping("/owner/{ownerId}")
    @PreAuthorize("hasRole('ADMIN') or @securityService.canAccessPerson(#ownerId)")
    public ResponseEntity<ApiResponse<List<CompanyResponseDTO>>> findByOwner(@PathVariable String ownerId) {
        return ResponseEntity.ok(ApiResponse.success(companyService.findByOwner(ownerId), "Empresas do owner"));
    }

    /**
     * Atualizar empresa.
     * Regra:
     *  - ADMIN pode tudo
     *  - USER só se for dono da empresa
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or @securityService.canAccessCompany(#id)")
    public ResponseEntity<ApiResponse<CompanyResponseDTO>> update(
            @PathVariable String id,
            @Valid
            @RequestBody CompanyRequestDTO dto
    ) {
        return ResponseEntity.ok(ApiResponse.success(companyService.update(id, dto), "Empresa atualizada"));
    }

    /**
     * Deletar empresa.
     * Regra:
     *  - ADMIN pode tudo
     *  - USER só se for dono da empresa
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or @securityService.canAccessCompany(#id)")
    public ResponseEntity<ApiResponse<Valid>> delete(@PathVariable String id) {
        companyService.delete(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Empresa deletada"));
    }

}
