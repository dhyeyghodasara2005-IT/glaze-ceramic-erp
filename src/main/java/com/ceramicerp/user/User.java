package com.ceramicerp.user;

import com.ceramicerp.common.BaseEntity;
import com.ceramicerp.organization.Plant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

/**
 * A person who can log in. Users are never hard-deleted (their name is on past records);
 * "removing" a user means setting active = false.
 */
@Getter
@Setter
@Entity
@Table(name = "app_users")
public class User extends BaseEntity {

    @Column(nullable = false, length = 50)
    private String username;

    @Column(nullable = false, length = 150)
    private String fullName;

    @Column(length = 20)
    private String phone;

    @Column(length = 150)
    private String email;

    /** BCrypt hash - the real password is never stored. */
    @Column(nullable = false, length = 100)
    private String passwordHash;

    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false)
    private boolean mustChangePassword = true;

    /** true = can work in every plant, including plants added later. */
    @Column(nullable = false)
    private boolean allPlantsAccess;

    /** "en", "gu" or "hi" - used by the frontend to pick the UI language. */
    @Column(nullable = false, length = 5)
    private String preferredLanguage = "en";

    private Instant lastLoginAt;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id"))
    private Set<Role> roles = new HashSet<>();

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "user_plants",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "plant_id"))
    private Set<Plant> plants = new HashSet<>();

    /** All permissions from all of this user's roles combined. */
    public Set<Permission> permissions() {
        Set<Permission> all = EnumSet.noneOf(Permission.class);
        roles.forEach(r -> all.addAll(r.getPermissions()));
        return all;
    }

    public boolean hasRole(String roleName) {
        return roles.stream().anyMatch(r -> r.getName().equals(roleName));
    }
}
