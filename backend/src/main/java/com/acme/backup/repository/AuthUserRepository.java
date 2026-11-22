package com.acme.backup.repository;

import com.acme.backup.domain.AuthUser;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthUserRepository extends JpaRepository<AuthUser, String> { }
