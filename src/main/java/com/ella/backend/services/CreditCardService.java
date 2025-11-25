package com.ella.backend.services;

import com.ella.backend.dto.CreditCardRequestDTO;
import com.ella.backend.dto.CreditCardResponseDTO;
import com.ella.backend.entities.CreditCard;
import com.ella.backend.entities.Person;
import com.ella.backend.exceptions.ResourceNotFoundException;
import com.ella.backend.repositories.CreditCardRepository;
import com.ella.backend.repositories.PersonRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CreditCardService {

    private final CreditCardRepository creditCardRepository;
    private final PersonRepository personRepository;

    public CreditCardResponseDTO create(CreditCardRequestDTO dto) {
        Person owner = personRepository.findById(UUID.fromString(dto.getOwnerId()))
                .orElseThrow(() -> new ResourceNotFoundException("Pessoa não encontrada"));

        CreditCard card = new CreditCard();
        card.setOwner(owner);
        card.setName(dto.getName());
        card.setBrand(dto.getBrand());
        card.setLimitAmount(dto.getLimitAmount());
        card.setClosingDay(dto.getClosingDay());
        card.setDueDay(dto.getDueDay());

        card = creditCardRepository.save(card);
        return toDTO(card);
    }

    public CreditCardResponseDTO findById(String id) {
        CreditCard card = creditCardRepository.findById(UUID.fromString(id))
                .orElseThrow(() -> new ResourceNotFoundException("Cartão não encontrado"));
        return toDTO(card);
    }

    public List<CreditCardResponseDTO> findByOwner(String ownerId) {
        Person owner = personRepository.findById(UUID.fromString(ownerId)).orElseThrow(
                () -> new ResourceNotFoundException("Pessoa não encontrada")
        );
        return creditCardRepository.findByOwner(owner).stream()
                .map(this::toDTO)
                .toList();
    }

    public List<CreditCardResponseDTO> findAll() {
        return creditCardRepository.findAll().stream().map(this::toDTO).toList();
    }

    public CreditCardResponseDTO update(String id, CreditCardRequestDTO dto) {
        CreditCard card = creditCardRepository.findById(UUID.fromString(id)).orElseThrow(
                () -> new ResourceNotFoundException("Cartão não encontrado")
        );

        Person owner = personRepository.findById(UUID.fromString(dto.getOwnerId())).orElseThrow(
                () -> new ResourceNotFoundException("Pessoa não encontrada")
        );

        card.setOwner(owner);
        card.setName(dto.getName());
        card.setBrand(dto.getBrand());
        card.setLimitAmount(dto.getLimitAmount());
        card.setClosingDay(dto.getClosingDay());
        card.setDueDay(dto.getDueDay());

        card = creditCardRepository.save(card);
        return toDTO(card);
    }

    public void delete(String id) {
        CreditCard card = creditCardRepository.findById(UUID.fromString(id))
                .orElseThrow(() -> new ResourceNotFoundException("Cartão não encontrado"));
        creditCardRepository.delete(card);
    }

    private CreditCardResponseDTO toDTO(CreditCard card) {
        CreditCardResponseDTO dto = new CreditCardResponseDTO();
        dto.setId(card.getId().toString());
        dto.setOwnerId(card.getOwner().getId().toString());
        dto.setOwnerName(card.getOwner().getName());
        dto.setName(card.getName());
        dto.setBrand(card.getBrand());
        dto.setLimitAmount(card.getLimitAmount());
        dto.setClosingDay(card.getClosingDay());
        dto.setDueDay(card.getDueDay());
        dto.setCreatedAt(card.getCreatedAt());
        dto.setUpdatedAt(card.getUpdatedAt());
        return dto;
    }
}
