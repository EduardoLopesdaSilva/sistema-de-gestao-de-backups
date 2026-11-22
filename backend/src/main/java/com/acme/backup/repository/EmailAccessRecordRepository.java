package com.acme.backup.repository;

import com.acme.backup.domain.EmailAccessRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EmailAccessRecordRepository extends JpaRepository<EmailAccessRecord, UUID> {
    List<EmailAccessRecord> findByCategoryOrderByCreatedAtDesc(String category);
    Optional<EmailAccessRecord> findBySourceFileAndSourceRow(String sourceFile, Integer sourceRow);
}
