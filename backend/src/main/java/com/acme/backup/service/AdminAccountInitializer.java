package com.acme.backup.service;

import com.acme.backup.domain.AppUser;
import com.acme.backup.repository.AppUserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import java.util.UUID;
import java.util.Map;

@Component
@ConditionalOnProperty(name = "app.security.seed-admin", havingValue = "true", matchIfMissing = true)
public class AdminAccountInitializer implements ApplicationRunner {
    private final AppUserRepository users;
    private final PasswordEncoder encoder;
    private final AuditService audit;
    private final String username;
    private final String password;
    public AdminAccountInitializer(AppUserRepository users, PasswordEncoder encoder, AuditService audit,
            @Value("${APP_ADMIN_USERNAME:admin}") String username,
            @Value("${APP_ADMIN_PASSWORD}") String password) {
        this.users = users; this.encoder = encoder; this.audit = audit; this.username = username; this.password = password;
    }
    @Override public void run(ApplicationArguments args) {
        if (password.length() < 16) throw new IllegalStateException("APP_ADMIN_PASSWORD deve ter ao menos 16 caracteres.");
        if (users.findByUsername(username).isPresent()) return;
        AppUser admin = new AppUser();
        admin.setId(UUID.randomUUID()); admin.setUsername(username); admin.setPasswordHash(encoder.encode(password));
        admin.setRole("ADMIN"); admin.setActive(true); users.save(admin);
        audit.record("system", null, null, "CREATE_INITIAL_ADMIN", "SUCCESS", Map.of("username", username, "role", "ADMIN"));
    }
}
