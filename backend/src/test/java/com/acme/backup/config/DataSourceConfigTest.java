package com.acme.backup.config;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DataSourceConfigTest {
    @Test void convertsLocalMysqlUriAndDisablesTlsOnlyForLocalServer() {
        assertEquals("jdbc:mysql://localhost:3306/ease_backup_control?sslMode=DISABLED",
                DataSourceConfig.toJdbcUrl("mysql://backup:secret@localhost/ease_backup_control"));
    }

    @Test void convertsRemoteMysqlUriAndRequiresTlsByDefault() {
        assertEquals("jdbc:mysql://mysql.example:3306/ease_backup_control?sslMode=REQUIRED",
                DataSourceConfig.toJdbcUrl("mysql://backup:secret@mysql.example/ease_backup_control"));
    }

    @Test void preservesExplicitTlsModeAndJdbcUrls() {
        assertEquals("jdbc:mysql://mysql.example:3307/ease_backup_control?sslMode=VERIFY_IDENTITY",
                DataSourceConfig.toJdbcUrl("mysql://backup:secret@mysql.example:3307/ease_backup_control?ssl-mode=VERIFY_IDENTITY"));
        assertEquals("jdbc:mysql://localhost:3306/ease_backup_control?sslMode=DISABLED",
                DataSourceConfig.toJdbcUrl("jdbc:mysql://localhost:3306/ease_backup_control?sslMode=DISABLED"));
        assertEquals("jdbc:mysql://mysql.example:3306/ease_backup_control?sslMode=REQUIRED",
                DataSourceConfig.toJdbcUrl("jdbc:mysql://mysql.example:3306/ease_backup_control"));
    }

    @Test void rejectsNonMysqlConnectionStrings() {
        assertThrows(IllegalArgumentException.class,
                () -> DataSourceConfig.toJdbcUrl("sqlserver://db.example/backup_control"));
    }
}
