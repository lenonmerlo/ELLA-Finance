package com.ella.backend.services;

import com.ella.backend.entities.Person;
import com.ella.backend.repositories.PersonRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PersonService {

    private final PersonRepository personRepository;

    public List<Person> findAll() {
        return personRepository.findAll();
    }

    public Person findById(String id) {
        return personRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Pessoa não encontrada"));
    }

    public Person create(Person person) {
        // TODO: Implementar validações aqui.
        return personRepository.save(person);
    }

    public Person update(String id, Person data) {
        Person existing = findById(id);

        // Atualizamos somente os campos editáveis
        existing.setName(data.getName());
        existing.setPhone(data.getPhone());
        existing.setBirthDate(data.getBirthDate());
        existing.setAddress(data.getAddress());
        existing.setIncome(data.getIncome());
        existing.setLanguage(data.getLanguage());
        existing.setCurrency(data.getCurrency());
        existing.setPlan(data.getPlan());

        return personRepository.save(existing);
    }

    public void delete(String id) {
        Person existing = findById(id);
        personRepository.delete(existing);
    }
}
