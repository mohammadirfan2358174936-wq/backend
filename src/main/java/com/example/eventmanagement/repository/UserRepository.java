package com.example.eventmanagement.repository;

import com.example.eventmanagement.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmail(String email);

    /** Counts for the admin dashboard. */
    long countByRole(com.example.eventmanagement.entity.Role role);

    long countByEnabledTrue();

    @Query("select count(u) from User u where u.createdAt >= :since")
    long countNewSince(@Param("since") java.time.Instant since);

    @Query("""
        select u from User u
        where u.createdAt >= :since
        order by u.createdAt desc
        """)
    java.util.List<User> findRecent(@Param("since") java.time.Instant since, Pageable pageable);

    Page<User> findByRole(com.example.eventmanagement.entity.Role role, Pageable pageable);
}
