package com.ella.backend.services;

import com.ella.backend.dto.CompanyRequestDTO;
import com.ella.backend.dto.CompanyResponseDTO;
import com.ella.backend.entities.Company;
import com.ella.backend.entities.Person;
import com.ella.backend.exceptions.ResourceNotFoundException;
import com.ella.backend.repositories.CompanyRepository;
import com.ella.backend.repositories.PersonRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CompanyService {

    private final CompanyRepository companyRepository;
    private final PersonRepository personRepository;

    public CompanyResponseDTO create(CompanyRequestDTO dto) {
        UUID ownerUuid = UUID.fromString(dto.getOwnerId());

        Person owner = personRepository.findById(ownerUuid)
                .orElseThrow(() -> new ResourceNotFoundException("Owner não encontrado"));

        Company c = new Company();
        c.setName(dto.getName());
        c.setDocument(dto.getDocument());
        c.setDescription(dto.getDescription());
        c.setOwner(owner);

        Company saved = companyRepository.save(c);
        return toDTO(saved);
    }

    public CompanyResponseDTO findById(String id) {
        UUID uuid = UUID.fromString(id);
        Company c = companyRepository.findById(uuid)
                .orElseThrow(() -> new ResourceNotFoundException("Empresa não encontrada"));

        return toDTO(c);
    }

    public List<CompanyResponseDTO> findAll() {
        return companyRepository.findAll()
                .stream()
                .map(this::toDTO)
                .toList();
    }

    public List<CompanyResponseDTO> findByOwner(String ownerId) {
        UUID uuid = UUID.fromString(ownerId);

        Person owner = personRepository.findById(uuid)
                .orElseThrow(() -> new ResourceNotFoundException("Owner não encontrado"));

        return companyRepository.findByOwner(owner)
                .stream()
                .map(this::toDTO)
                .toList();
    }

    public CompanyResponseDTO update(String id, CompanyRequestDTO dto) {
        UUID uuid = UUID.fromString(id);

        Company existing = companyRepository.findById(uuid)
                .orElseThrow(() -> new ResourceNotFoundException("Empresa não encontrada"));

        UUID ownerUuid = UUID.fromString(dto.getOwnerId());

        Person owner = personRepository.findById(ownerUuid)
                .orElseThrow(() -> new ResourceNotFoundException("Owner não encontrado"));

        existing.setName(dto.getName());
        existing.setDocument(dto.getDocument());
        existing.setDescription(dto.getDescription());
        existing.setOwner(owner);

        companyRepository.save(existing);

        return toDTO(existing);
    }

    public void delete(String id) {
        UUID uuid = UUID.fromString(id);

        Company c = companyRepository.findById(uuid)
                .orElseThrow(() -> new ResourceNotFoundException("Empresa não encontrada"));

        companyRepository.delete(c);
    }

    private CompanyResponseDTO toDTO(Company c) {
        CompanyResponseDTO dto = new CompanyResponseDTO();

        dto.setId(c.getId().toString());
        dto.setName(c.getName());
        dto.setDocument(c.getDocument());
        dto.setDescription(c.getDescription());
        dto.setOwnerId(c.getOwner().getId().toString());
        dto.setOwnerName(c.getOwner().getName());
        dto.setCreatedAt(c.getCreatedAt());
        dto.setUpdatedAt(c.getUpdatedAt());

        return dto;
    }
}
