package com.acme.backup.repository;

import com.acme.backup.domain.ImportRun;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface ImportRunRepository extends JpaRepository<ImportRun, UUID> {
    boolean existsBySourceChecksum(String sourceChecksum);
}
