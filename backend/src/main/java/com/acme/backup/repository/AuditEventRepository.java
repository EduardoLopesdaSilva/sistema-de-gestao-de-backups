package com.acme.backup.repository;

import com.acme.backup.domain.AuditEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID> {
    Page<AuditEvent> findByVolumeIdContainingIgnoreCaseAndActionContainingIgnoreCase(String volumeId, String action, Pageable pageable);
}
