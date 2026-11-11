package com.acme.backup.web;

import com.acme.backup.domain.AppUser;
import com.acme.backup.repository.AppUserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import com.acme.backup.service.AuditService;
import java.util.Map;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/users")
public class AdminUserController {
    private static final Set<String> ROLES = Set.of("ADMIN", "OPERATOR", "READER");
    private final AppUserRepository users;
    private final PasswordEncoder passwords;
    private final AuditService audit;
    public AdminUserController(AppUserRepository users, PasswordEncoder passwords, AuditService audit) { this.users = users; this.passwords = passwords; this.audit = audit; }

    @GetMapping @Transactional(readOnly = true)
    public List<UserView> list() { return users.findAll().stream().map(AdminUserController::view).toList(); }

    @PostMapping @Transactional
    @ResponseStatus(HttpStatus.CREATED)
    public UserView create(@Valid @RequestBody CreateUserRequest request, java.security.Principal principal) {
        if (users.findByUsername(request.username()).isPresent()) throw new ResponseStatusException(HttpStatus.CONFLICT, "Usuário já cadastrado.");
        if (!ROLES.contains(request.role())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Papel inválido.");
        AppUser user = new AppUser(); user.setId(UUID.randomUUID()); user.setUsername(request.username());
        user.setPasswordHash(passwords.encode(request.password())); user.setRole(request.role()); user.setActive(true);
        AppUser saved = users.save(user);
        audit.record(principal.getName(), null, null, "CREATE_USER", "SUCCESS", Map.of("username", saved.getUsername(), "role", saved.getRole()));
        return view(saved);
    }

    @PatchMapping("/{id}/status") @Transactional
    public UserView setActive(@PathVariable UUID id, @RequestBody StatusRequest request, java.security.Principal principal) {
        AppUser user = users.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuário não encontrado."));
        if (!request.active() && user.getUsername().equals(principal.getName()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Não é possível desativar a própria conta.");
        if (!request.active() && "ADMIN".equals(user.getRole()) && users.countByRoleAndActiveTrue("ADMIN") <= 1)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "É necessário manter ao menos um administrador ativo.");
        user.setActive(request.active());
        AppUser saved = users.save(user);
        audit.record(principal.getName(), null, null, "SET_USER_STATUS", "SUCCESS", Map.of("username", saved.getUsername(), "active", saved.isActive()));
        return view(saved);
    }

    private static UserView view(AppUser user) { return new UserView(user.getId(), user.getUsername(), user.getRole(), user.isActive()); }
    public record CreateUserRequest(
        @NotBlank @Pattern(regexp = "^[a-zA-Z0-9._-]{3,64}$") String username,
        @NotBlank @Size(min = 16, max = 128) String password,
        @NotBlank String role) {}
    public record StatusRequest(boolean active) {}
    public record UserView(UUID id, String username, String role, boolean active) {}
}
