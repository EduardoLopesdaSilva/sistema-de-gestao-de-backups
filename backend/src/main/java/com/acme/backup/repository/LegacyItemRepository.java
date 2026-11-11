package com.acme.backup.repository;

import com.acme.backup.domain.LegacyItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LegacyItemRepository extends JpaRepository<LegacyItem, String> {
    @Query(value = """
        SELECT li.* FROM legacy_item li
        WHERE (:volume = '' OR li.volume_name = :volume)
          AND (:q = '' OR to_tsvector('simple', coalesce(li.label, '')) @@ plainto_tsquery('simple', :q)
               OR lower(li.label) LIKE lower(concat('%', :q, '%'))
               OR lower(li.volume_name) LIKE lower(concat('%', :q, '%')))
        ORDER BY li.volume_name, li.label
        """,
        countQuery = """
        SELECT count(*) FROM legacy_item li
        WHERE (:volume = '' OR li.volume_name = :volume)
          AND (:q = '' OR to_tsvector('simple', coalesce(li.label, '')) @@ plainto_tsquery('simple', :q)
               OR lower(li.label) LIKE lower(concat('%', :q, '%'))
               OR lower(li.volume_name) LIKE lower(concat('%', :q, '%')))
        """,
        nativeQuery = true)
    Page<LegacyItem> search(@Param("q") String query, @Param("volume") String volume, Pageable pageable);
}
