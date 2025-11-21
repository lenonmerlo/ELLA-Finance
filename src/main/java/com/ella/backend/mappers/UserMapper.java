package com.ella.backend.mappers;

import com.ella.backend.dto.UserRequestDTO;
import com.ella.backend.dto.UserResponseDTO;
import com.ella.backend.entities.User;

public class UserMapper {

    private UserMapper() {

    }

    // DTO de entrada -> Entidade (usado em create/update)
    public static User toEntity(UserRequestDTO dto) {
        if (dto == null) return null;

        User u = new User();

        //Person
        u.setName(dto.getName());
        u.setPhone(dto.getPhone());
        u.setBirthDate(dto.getBirthDate());
        u.setAddress(dto.getAddress());
        u.setIncome(dto.getIncome());

        // Só sobrescreve os campos que vierem preenchidos
        if (dto.getLanguage() != null) {
            u.setLanguage(dto.getLanguage());
        }

        if (dto.getPlan() !=null) {
            u.setPlan(dto.getPlan());
        }

        if (dto.getCurrency() != null) {
            u.setCurrency(dto.getCurrency());
        }

        if (dto.getStatus() !=null) {
            u.setStatus(dto.getStatus());
        }

        //User
        u.setEmail(dto.getEmail());
        u.setPassword(dto.getPassword());
        if (dto.getRole() != null) {
            u.setRole(dto.getRole());
        }

        return u;
    }

    // Entidade -> DTO de resposta (Não expoe senha)
    public static UserResponseDTO toResponseDTO(User u) {
        if (u == null) return null;

        UserResponseDTO dto = new UserResponseDTO();

        dto.setId(u.getId());

        // Person
        dto.setName(u.getName());
        dto.setPhone(u.getPhone());
        dto.setBirthDate(u.getBirthDate());
        dto.setAddress(u.getAddress());
        dto.setIncome(u.getIncome());
        dto.setLanguage(u.getLanguage());
        dto.setPlan(u.getPlan());
        dto.setCurrency(u.getCurrency());
        dto.setStatus(u.getStatus());

        // User
        dto.setEmail(u.getEmail());
        dto.setRole(u.getRole());

        dto.setCreatedAt(u.getCreatedAt());
        dto.setUpdatedAt(u.getUpdatedAt());

        return dto;
    }
}
