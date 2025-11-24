package com.ella.backend.services;

import com.ella.backend.entities.Person;
import com.ella.backend.exceptions.ResourceNotFoundException;
import com.ella.backend.repositories.PersonRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

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

    public Person create(Person person) {
        // aqui adicionar validações de negócio
        return personRepository.save(person);
    }

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

        return personRepository.save(existing);
    }

    public void delete(String id) {
        Person existing = findById(id);
        personRepository.delete(existing);
    }
}
