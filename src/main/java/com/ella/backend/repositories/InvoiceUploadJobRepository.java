package com.ella.backend.repositories;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ella.backend.entities.InvoiceUploadJob;

public interface InvoiceUploadJobRepository extends JpaRepository<InvoiceUploadJob, UUID> {
    Optional<InvoiceUploadJob> findByIdAndPersonId(UUID id, UUID personId);

    Optional<InvoiceUploadJob> findTopByPersonIdAndFileSha256OrderByCreatedAtDesc(UUID personId, String fileSha256);
}
