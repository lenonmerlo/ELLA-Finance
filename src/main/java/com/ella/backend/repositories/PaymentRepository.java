package com.ella.backend.repositories;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ella.backend.entities.Payment;
import com.ella.backend.entities.User;
import com.ella.backend.enums.PaymentStatus;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    List<Payment> findByUser(User user);

    interface LastPaymentInfo {
        UUID getUserId();

        LocalDateTime getLastPaidAt();
    }

    @Query("""
            select p.user.id as userId, max(p.paidAt) as lastPaidAt
            from Payment p
            where p.user.id in :userIds
              and p.status = :status
              and p.paidAt is not null
            group by p.user.id
            """)
    List<LastPaymentInfo> findLastPaidAtByUserIds(
            @Param("userIds") Collection<UUID> userIds,
            @Param("status") PaymentStatus status
    );
}
