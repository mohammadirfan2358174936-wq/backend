package com.example.eventmanagement.repository;

import com.example.eventmanagement.entity.Payment;
import com.example.eventmanagement.entity.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByRegistrationId(Long registrationId);

    Optional<Payment> findByTransactionRef(String transactionRef);

    @Query("select coalesce(sum(p.amount), 0) from Payment p where p.status = :status")
    double sumAmountByStatus(@Param("status") PaymentStatus status);

    @Query("select p from Payment p where p.registration.id = :registrationId")
    List<Payment> findAllForRegistration(@Param("registrationId") Long registrationId);

    boolean existsByTransactionRef(String transactionRef);
}
