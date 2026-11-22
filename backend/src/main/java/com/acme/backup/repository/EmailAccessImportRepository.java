package com.acme.backup.repository;

import com.acme.backup.domain.EmailAccessImport;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailAccessImportRepository extends JpaRepository<EmailAccessImport, String> { }
