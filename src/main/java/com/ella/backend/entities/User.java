package com.ella.backend.entities;

import com.ella.backend.enums.Role;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Entity
@Table(name = "users")
@PrimaryKeyJoinColumn(name = "person_id")
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

    @Basic(fetch = FetchType.LAZY)
    @Column(name = "avatar", columnDefinition = "bytea")
    private byte[] avatar;

    @Column(name = "avatar_content_type", length = 100)
    private String avatarContentType;
}
