package com.acme.backup.service;

import com.acme.backup.domain.AuthUser;
import com.acme.backup.repository.AuthUserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class AuthUserInitializer implements ApplicationRunner {
    private final AuthUserRepository users;
    private final PasswordEncoder passwords;
    private final String initialPassword;

    public AuthUserInitializer(AuthUserRepository users, PasswordEncoder passwords,
            @Value("${app.auth.initial-password}") String initialPassword) {
        this.users = users;
        this.passwords = passwords;
        this.initialPassword = initialPassword;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (users.existsById("ti3")) return;
        AuthUser user = new AuthUser();
        user.setUsername("ti3");
        user.setPasswordHash(passwords.encode(initialPassword));
        users.save(user);
    }
}
