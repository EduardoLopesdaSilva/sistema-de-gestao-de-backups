package com.acme.backup.repository;

import com.acme.backup.domain.BackupRecordImport;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BackupRecordImportRepository extends JpaRepository<BackupRecordImport, String> {}
