package com.acme.backup.domain;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "app_user")
public class AppUser {
    @Id private UUID id;
    @Column(nullable = false, unique = true) private String username;
    @Column(name = "password_hash", nullable = false) private String passwordHash;
    @Column(nullable = false) private String role;
    @Column(nullable = false) private boolean active;
    public AppUser() {}
    public UUID getId() { return id; }
    public void setId(UUID value) { id = value; }
    public String getUsername() { return username; }
    public void setUsername(String value) { username = value; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String value) { passwordHash = value; }
    public String getRole() { return role; }
    public void setRole(String value) { role = value; }
    public boolean isActive() { return active; }
    public void setActive(boolean value) { active = value; }
}
