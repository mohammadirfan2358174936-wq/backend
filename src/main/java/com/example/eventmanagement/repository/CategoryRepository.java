package com.example.eventmanagement.repository;

import com.example.eventmanagement.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {

    Optional<Category> findBySlug(String slug);

    boolean existsBySlug(String slug);

    /** Ordered for display; event counts attached client-side from the admin panel. */
    List<Category> findAllByOrderByNameAsc();

    /**
     * Popular categories with their live event counts, in one round trip.
     * Used by the admin dashboard — a GROUP BY beats N+1 fetches.
     */
    @Query("""
        select c.name as name, count(e.id) as count
        from Category c left join Event e on e.category = c and e.deleted = false
        group by c.id, c.name
        having count(e.id) > 0
        order by count(e.id) desc
        """)
    List<CategoryCount> findPopularWithCounts();

    interface CategoryCount {
        String getName();

        Long getCount();
    }
}
