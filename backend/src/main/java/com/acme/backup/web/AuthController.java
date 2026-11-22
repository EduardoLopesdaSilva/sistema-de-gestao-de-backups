package com.acme.backup.web;

import com.acme.backup.domain.AuthUser;
import com.acme.backup.repository.AuthUserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api")
public class AuthController {
    public static final String SESSION_USER = "AUTHENTICATED_USERNAME";
    private static final int MAX_ATTEMPTS = 6;
    private static final long WINDOW_MILLIS = 15 * 60 * 1000L;
    private final AuthUserRepository users;
    private final PasswordEncoder passwords;
    private final String dummyHash;
    private final ConcurrentHashMap<String, AttemptWindow> attempts = new ConcurrentHashMap<>();

    public AuthController(AuthUserRepository users, PasswordEncoder passwords) {
        this.users = users;
        this.passwords = passwords;
        this.dummyHash = passwords.encode(UUID.randomUUID().toString());
    }

    @PostMapping("/login")
    public SessionView login(@Valid @RequestBody LoginRequest credentials, HttpServletRequest request) {
        String address = request.getRemoteAddr() == null ? "unknown" : request.getRemoteAddr();
        AttemptWindow window = attempts.computeIfAbsent(address, ignored -> new AttemptWindow());
        if (!window.tryAttempt(System.currentTimeMillis()))
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Muitas tentativas. Aguarde 15 minutos e tente novamente.");

        String username = credentials.username().strip().toLowerCase();
        AuthUser user = users.findById(username).orElse(null);
        boolean matches = passwords.matches(credentials.password(), user == null ? dummyHash : user.getPasswordHash());
        if (user == null || !matches) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Login ou senha incorretos.");

        attempts.remove(address);
        request.getSession(true);
        request.changeSessionId();
        request.getSession(false).setAttribute(SESSION_USER, user.getUsername());
        return new SessionView(user.getUsername());
    }

    @GetMapping("/session")
    public SessionView session(HttpServletRequest request) {
        return new SessionView((String) request.getSession(false).getAttribute(SESSION_USER));
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) session.invalidate();
    }

    @PostMapping("/change-password")
    @Transactional
    public void changePassword(@Valid @RequestBody ChangePasswordRequest body, HttpServletRequest request) {
        String username = (String) request.getSession(false).getAttribute(SESSION_USER);
        AuthUser user = users.findById(username).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sessão inválida."));
        if (!passwords.matches(body.currentPassword(), user.getPasswordHash()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A senha atual está incorreta.");
        user.setPasswordHash(passwords.encode(body.newPassword()));
        users.save(user);
        request.changeSessionId();
    }

    public record LoginRequest(@NotBlank @Size(max = 80) String username,
            @NotBlank @Size(max = 200) String password) { }

    public record ChangePasswordRequest(@NotBlank @Size(max = 200) String currentPassword,
            @NotBlank @Size(min = 12, max = 200) String newPassword) { }

    public record SessionView(String username) { }

    private static final class AttemptWindow {
        private long startedAt;
        private int count;
        private synchronized boolean tryAttempt(long now) {
            if (now - startedAt >= WINDOW_MILLIS) { startedAt = now; count = 0; }
            if (count >= MAX_ATTEMPTS) return false;
            count++;
            return true;
        }
    }
}
