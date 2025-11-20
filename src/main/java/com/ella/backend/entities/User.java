package com.ella.backend.entities;

import com.ella.backend.enums.*;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;


@Entity
@Table(name = "users")
@Data
@EqualsAndHashCode(callSuper = true)
public class User extends Person {

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role = Role.USER;
}
