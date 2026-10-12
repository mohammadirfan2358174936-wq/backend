package com.example.eventmanagement.repository;

import com.example.eventmanagement.entity.Ticket;
import com.example.eventmanagement.entity.TicketStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface TicketRepository extends JpaRepository<Ticket, Long> {

    Optional<Ticket> findByTicketNumber(String ticketNumber);

    boolean existsByTicketNumber(String ticketNumber);

    /**
     * Ticket lookup for the digital ticket view. The vendor columns are
     * denormalised onto the ticket itself, so no joins are needed — a single
     * index seek on `ticket_number`.
     */
    @Query("""
        select t from Ticket t
        where t.ticketNumber = :ticketNumber
        """)
    Optional<Ticket> findByNumberForDisplay(@Param("ticketNumber") String ticketNumber);

    @Query(
        value = """
            select t from Ticket t
            join fetch t.event e
            left join fetch e.category c
            where t.user.id = :userId
            order by t.issuedAt desc
            """,
        countQuery = "select count(t) from Ticket t where t.user.id = :userId")
    Page<Ticket> findByUserPaged(@Param("userId") Long userId, Pageable pageable);

    List<Ticket> findByRegistrationId(Long registrationId);

    @Query("select t from Ticket t where t.registration.id = :registrationId and t.status in :statuses")
    List<Ticket> findByRegistrationAndStatus(@Param("registrationId") Long registrationId,
                                            @Param("statuses") Collection<TicketStatus> statuses);

    long countByStatus(TicketStatus status);
}
