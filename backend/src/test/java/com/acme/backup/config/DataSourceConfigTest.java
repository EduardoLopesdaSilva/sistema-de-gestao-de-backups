package com.acme.backup.config;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class DataSourceConfigTest {
    @Test void convertsNeonUriAndRequiresTlsByDefault() {
        assertEquals("jdbc:postgresql://db.example:5432/neondb?sslmode=require",
            DataSourceConfig.toJdbcUrl("postgresql://user:pass@db.example/neondb"));
    }
    @Test void preservesExplicitSslAndExistingJdbcUrl() {
        assertEquals("jdbc:postgresql://db.example:5432/neondb?sslmode=verify-full",
            DataSourceConfig.toJdbcUrl("postgres://user:pass@db.example:5432/neondb?sslmode=verify-full"));
        assertEquals("jdbc:postgresql://localhost:5432/backup_control",
            DataSourceConfig.toJdbcUrl("jdbc:postgresql://localhost:5432/backup_control"));
    }
}
