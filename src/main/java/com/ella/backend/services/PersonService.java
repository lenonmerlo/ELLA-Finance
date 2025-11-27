package com.ella.backend.services;

import com.ella.backend.entities.Person;
import com.ella.backend.enums.Status;
import com.ella.backend.exceptions.BadRequestException;
import com.ella.backend.exceptions.ResourceNotFoundException;
import com.ella.backend.repositories.PersonRepository;
import com.ella.backend.audit.Auditable;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PersonService {

    private final PersonRepository personRepository;

    public List<Person> findAll() {
        return personRepository.findAll();
    }

    public Person findById(String id) {
        UUID uuid = UUID.fromString(id);
        return personRepository.findById(uuid)
                .orElseThrow(() -> new ResourceNotFoundException("Pessoa não encontrada"));
    }

    @Auditable(action = "PERSON_CREATED", entityType = "Person")
    public Person create(Person person) {
        applyPersonDefaults(person);
        validatePersonBusinessRules(person);
        return personRepository.save(person);
    }

    @Auditable(action = "PERSON_UPDATED", entityType = "Person")
    public Person update(String id, Person data) {
        Person existing = findById(id);

        existing.setName(data.getName());
        existing.setPhone(data.getPhone());
        existing.setBirthDate(data.getBirthDate());
        existing.setAddress(data.getAddress());
        existing.setIncome(data.getIncome());
        existing.setLanguage(data.getLanguage());
        existing.setCurrency(data.getCurrency());
        existing.setPlan(data.getPlan());
        existing.setStatus(data.getStatus());

        applyPersonDefaults(existing);
        validatePersonBusinessRules(existing);

        return personRepository.save(existing);
    }

    @Auditable(action = "PERSON_DELETED", entityType = "Person")
    public void delete(String id) {
        Person existing = findById(id);
        personRepository.delete(existing);
    }

    private void applyPersonDefaults(Person person) {
        if (person.getStatus() == null) {
            person.setStatus(Status.ACTIVE);
        }
    }

    private void validatePersonBusinessRules(Person person) {
        if (person.getName() == null || person.getName().isBlank()) {
            throw new BadRequestException("Nome é obrigatório");
        }

        if (person.getBirthDate() != null && person.getBirthDate().isAfter(LocalDate.now())) {
            throw new BadRequestException("Data de nascimento não pode ser futura");
        }

        if (person.getIncome() != null && person.getIncome().compareTo(BigDecimal.ZERO) < 0) {
            throw new BadRequestException("Renda não pode ser negativa");
        }
    }
}