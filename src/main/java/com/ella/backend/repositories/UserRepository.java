package com.ella.backend.repositories;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ella.backend.entities.User;
import com.ella.backend.enums.Role;
import com.ella.backend.enums.Status;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByRole(Role role);

    @Query("""
            select u from User u
            where (:q is null
                or lower(u.email) like lower(concat('%', cast(:q as string), '%'))
                or lower(u.name) like lower(concat('%', cast(:q as string), '%'))
            )
            and (:role is null or u.role = :role)
            and (:status is null or u.status = :status)
            """)
    Page<User> search(
            @Param("q") String q,
            @Param("role") Role role,
            @Param("status") Status status,
            Pageable pageable
    );
}
