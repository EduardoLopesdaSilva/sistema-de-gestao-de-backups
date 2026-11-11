package com.acme.backup.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import javax.sql.DataSource;
import java.net.URI;

@Configuration
public class DataSourceConfig {
    @Bean DataSource dataSource(@Value("${DATABASE_URL:jdbc:postgresql://localhost:5432/backup_control}") String connectionUrl,
            @Value("${PGUSER:backup}") String username, @Value("${PGPASSWORD}") String password) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(toJdbcUrl(connectionUrl));
        config.setUsername(username);
        config.setPassword(password);
        config.setMaximumPoolSize(8);
        config.setConnectionTimeout(10000);
        return new HikariDataSource(config);
    }

    static String toJdbcUrl(String connectionUrl) {
        if (connectionUrl.startsWith("jdbc:postgresql://")) return connectionUrl;
        URI uri = URI.create(connectionUrl);
        if (!("postgresql".equals(uri.getScheme()) || "postgres".equals(uri.getScheme())) || uri.getHost() == null)
            throw new IllegalArgumentException("DATABASE_URL deve apontar para PostgreSQL.");
        String query = uri.getRawQuery();
        if (query == null || !query.matches(".*(?:^|&)sslmode=[^&]+.*")) query = query == null ? "sslmode=require" : query + "&sslmode=require";
        int port = uri.getPort() < 0 ? 5432 : uri.getPort();
        return "jdbc:postgresql://" + uri.getHost() + ":" + port + uri.getRawPath() + "?" + query;
    }
}
