package com.ella.backend.controllers;

import com.ella.backend.entities.User;
import com.ella.backend.services.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/health")
    public String health() {
        return "OK - Ella Backend";
    }

    @GetMapping
    public List<User> listAll() {
        List<User> users = userService.findAll();
        users.forEach(u -> u.setPassword(null));
        return users;
    }

    @GetMapping("/{id}")
    public ResponseEntity<User> findById(@PathVariable String id) {
        User user = userService.findById(id);
        user.setPassword(null);
        return ResponseEntity.ok(user);
    }

    @PostMapping
    public ResponseEntity<User> create(@RequestBody User user) {
        User created = userService.create(user);
        // ⚠️ depois vamos trocar pra DTO pra não devolver a senha
        created.setPassword(null);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<User> update(@PathVariable String id,
                                       @RequestBody User user) {
        User updated = userService.update(id, user);
        updated.setPassword(null);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        userService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
