package com.acme.backup.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

@Configuration
public class DataSourceConfig {
    @Bean
    DataSource dataSource(
            @Value("${DATABASE_URL:jdbc:mysql://localhost:3307/ease_backup_control}") String connectionUrl,
            @Value("${DB_USERNAME:${MYSQL_USER:}}") String username,
            @Value("${DB_PASSWORD:${MYSQL_PASSWORD:}}") String password) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(toJdbcUrl(connectionUrl));
        String resolvedUsername = hasText(username) ? username : uriCredential(connectionUrl, true);
        String resolvedPassword = hasText(password) ? password : uriCredential(connectionUrl, false);
        if (hasText(resolvedUsername)) config.setUsername(resolvedUsername);
        if (hasText(resolvedPassword)) config.setPassword(resolvedPassword);
        config.setMaximumPoolSize(4);
        config.setMinimumIdle(1);
        config.setConnectionTimeout(10000);
        return new HikariDataSource(config);
    }

    static String toJdbcUrl(String connectionUrl) {
        boolean jdbcUrl = connectionUrl.startsWith("jdbc:mysql://");
        String uriValue = jdbcUrl ? connectionUrl.substring("jdbc:".length()) : connectionUrl;
        URI uri = URI.create(uriValue);
        if (!("mysql".equals(uri.getScheme()) || "mariadb".equals(uri.getScheme())) || uri.getHost() == null)
            throw new IllegalArgumentException("DATABASE_URL deve apontar para MySQL.");

        int port = uri.getPort() < 0 ? 3306 : uri.getPort();
        String query = uri.getRawQuery();
        if (query != null) query = query.replaceAll("(^|&)ssl-mode=", "$1sslMode=");
        if (query == null || !query.matches(".*(?:^|&)sslMode=[^&]+.*")) {
            String sslMode = isLocal(uri.getHost()) ? "DISABLED" : "REQUIRED";
            query = query == null ? "sslMode=" + sslMode : query + "&sslMode=" + sslMode;
        }
        return "jdbc:mysql://" + uri.getHost() + ":" + port + uri.getRawPath() + "?" + query;
    }

    private static String uriCredential(String connectionUrl, boolean user) {
        if (connectionUrl.startsWith("jdbc:mysql://")) return "";
        URI uri = URI.create(connectionUrl);
        String info = uri.getRawUserInfo();
        if (info == null) return "";
        int separator = info.indexOf(':');
        String value = user ? (separator < 0 ? info : info.substring(0, separator))
                : (separator < 0 ? "" : info.substring(separator + 1));
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private static boolean isLocal(String host) {
        return "localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host) || "::1".equals(host);
    }

    private static boolean hasText(String value) { return value != null && !value.isBlank(); }
}
