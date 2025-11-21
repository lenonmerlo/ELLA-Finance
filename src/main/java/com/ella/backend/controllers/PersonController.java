package com.ella.backend.controllers;

import com.ella.backend.dto.ApiResponse;
import com.ella.backend.dto.PersonRequestDTO;
import com.ella.backend.dto.PersonResponseDTO;
import com.ella.backend.entities.Person;
import com.ella.backend.mappers.PersonMapper;
import com.ella.backend.services.PersonService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
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
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<PersonResponseDTO>>> listAll() {
        List<Person> persons = personService.findAll();
        List<PersonResponseDTO> dtos = persons.stream()
                .map(PersonMapper::toResponseDTO)
                .toList();

        ApiResponse<List<PersonResponseDTO>> body = ApiResponse.<List<PersonResponseDTO>>builder()
                .success(true)
                .data(dtos)
                .message("Pessoas listadas com sucesso")
                .timestamp(Instant.now())
                .build();

        return ResponseEntity.ok(body);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or @securityService.isCurrentUser(#id)")
    public ResponseEntity<ApiResponse<PersonResponseDTO>> findById(@PathVariable String id) {
        Person person = personService.findById(id);
        PersonResponseDTO dto = PersonMapper.toResponseDTO(person);

        ApiResponse<PersonResponseDTO> body = ApiResponse.<PersonResponseDTO>builder()
                .success(true)
                .data(dto)
                .message("Pessoa encontrada")
                .timestamp(Instant.now())
                .build();

        return ResponseEntity.ok(body);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<PersonResponseDTO>> create(@RequestBody PersonRequestDTO request) {
        Person entity = PersonMapper.toEntity(request);
        Person created = personService.create(entity);
        PersonResponseDTO dto = PersonMapper.toResponseDTO(created);

        ApiResponse<PersonResponseDTO> body = ApiResponse.<PersonResponseDTO>builder()
                .success(true)
                .data(dto)
                .message("Pessoa criada com sucesso")
                .timestamp(Instant.now())
                .build();

        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or @securityService.isCurrentUser(#id)")
    public ResponseEntity<ApiResponse<PersonResponseDTO>> update(@PathVariable String id,
                                                                 @RequestBody PersonRequestDTO request) {
        Person entity = PersonMapper.toEntity(request);
        Person updated = personService.update(id, entity);
        PersonResponseDTO dto = PersonMapper.toResponseDTO(updated);

        ApiResponse<PersonResponseDTO> body = ApiResponse.<PersonResponseDTO>builder()
                .success(true)
                .data(dto)
                .message("Pessoa atualizada com sucesso")
                .timestamp(Instant.now())
                .build();

        return ResponseEntity.ok(body);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable String id) {
        personService.delete(id);

        ApiResponse<Void> body = ApiResponse.<Void>builder()
                .success(true)
                .data(null)
                .message("Pessoa deletada com sucesso")
                .timestamp(Instant.now())
                .build();

        return ResponseEntity.status(HttpStatus.NO_CONTENT).body(body);
    }
}
