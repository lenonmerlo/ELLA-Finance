package com.ella.backend.mappers;

import com.ella.backend.dto.PersonRequestDTO;
import com.ella.backend.dto.PersonResponseDTO;
import com.ella.backend.entities.Person;
import org.springframework.security.access.method.P;

public class PersonMapper {
    private PersonMapper(){}

    public static Person toEntity(PersonRequestDTO dto) {
        if (dto == null) return null;

        Person p = new Person();
        p.setName(dto.getName());
        p.setPhone(dto.getPhone());
        p.setBirthDate(dto.getBirthDate());
        p.setAddress(dto.getAddress());
        p.setIncome(dto.getIncome());

        if (dto.getLanguage() != null) {
            p.setLanguage(dto.getLanguage());
        }

        if (dto.getPlan() != null) {
            p.setPlan(dto.getPlan());
        }

        if (dto.getCurrency() != null) {
            p.setCurrency(dto.getCurrency());
        }

        if (dto.getStatus() != null) {
            p.setStatus(dto.getStatus());
        }

        return p;
    }

    public static PersonResponseDTO toResponseDTO(Person p) {
        if (p ==null) return null;

        PersonResponseDTO dto = new PersonResponseDTO();

        dto.setId(p.getId().toString());
        dto.setName(p.getName());
        dto.setPhone(p.getPhone());
        dto.setBirthDate(p.getBirthDate());
        dto.setAddress(p.getAddress());
        dto.setLanguage(p.getLanguage());
        dto.setPlan(p.getPlan());
        dto.setStatus(p.getStatus());
        dto.setCreatedAt(p.getCreatedAt());
        dto.setUpdatedAt(p.getUpdatedAt());

        return dto;
    }

}

