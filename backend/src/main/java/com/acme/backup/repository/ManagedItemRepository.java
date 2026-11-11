package com.acme.backup.repository;

import com.acme.backup.domain.ManagedItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ManagedItemRepository extends JpaRepository<ManagedItem, UUID> {
    Page<ManagedItem> findByVolumeIdAndParentId(String volumeId, UUID parentId, Pageable pageable);
    boolean existsByVolumeIdAndRelativePath(String volumeId, String relativePath);
    boolean existsByVolumeIdAndParentIdAndDisplayName(String volumeId, UUID parentId, String displayName);
    List<ManagedItem> findBySha256(String sha256);
    Page<ManagedItem> findByItemKindAndCreatedAtBefore(String itemKind, Instant before, Pageable pageable);
    @Query("select m.sha256, count(m), sum(m.sizeBytes) from ManagedItem m where m.itemKind = 'FILE' and m.sha256 is not null group by m.sha256 having count(m) > 1")
    List<Object[]> findDuplicateHashGroups();
}
