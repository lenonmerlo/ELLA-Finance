package com.ella.backend.controllers;

import com.ella.backend.entities.Person;
import com.ella.backend.services.PersonService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/persons")
@RequiredArgsConstructor
public class PersonController {

    private final PersonService personService;

    @GetMapping("/health")
    public String health() {
        return "OK - Ella Persons";
    }

    @GetMapping
    public List<Person> listAll() {
        return personService.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Person> findById(@PathVariable String id) {
        Person person = personService.findById(id);
        return ResponseEntity.ok(person);
    }

    @PostMapping
    public ResponseEntity<Person> create(@RequestBody Person person) {
        Person created = personService.create(person);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Person> update(@PathVariable String id,
                                         @RequestBody Person person) {
        Person updated = personService.update(id, person);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Person> delete(@PathVariable String id) {
        personService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
