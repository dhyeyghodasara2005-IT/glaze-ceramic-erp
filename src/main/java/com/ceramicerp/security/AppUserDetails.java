package com.ceramicerp.security;

import com.ceramicerp.organization.Plant;
import com.ceramicerp.user.Permission;
import com.ceramicerp.user.User;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The logged-in user as Spring Security sees it during one request.
 * Each permission becomes an "authority", which is what @PreAuthorize("hasAuthority('X')") checks.
 */
@Getter
public class AppUserDetails implements UserDetails {

    private final Long userId;
    private final String username;
    private final String passwordHash;
    private final String fullName;
    private final boolean active;
    private final boolean allPlantsAccess;
    private final Set<Long> plantIds;
    private final Set<Permission> permissions;
    private final Set<GrantedAuthority> authorities;

    private AppUserDetails(User u) {
        this.userId = u.getId();
        this.username = u.getUsername();
        this.passwordHash = u.getPasswordHash();
        this.fullName = u.getFullName();
        this.active = u.isActive();
        this.allPlantsAccess = u.isAllPlantsAccess();
        this.plantIds = Collections.unmodifiableSet(
                u.getPlants().stream().map(Plant::getId).collect(Collectors.toSet()));
        Set<Permission> perms = u.permissions();
        this.permissions = Collections.unmodifiableSet(perms.isEmpty() ? EnumSet.noneOf(Permission.class) : EnumSet.copyOf(perms));
        this.authorities = perms.stream()
                .map(p -> (GrantedAuthority) new SimpleGrantedAuthority(p.name()))
                .collect(Collectors.toUnmodifiableSet());
    }

    /** Must be called inside a transaction (reads the lazy plants collection). */
    public static AppUserDetails from(User user) {
        return new AppUserDetails(user);
    }

    public boolean canAccessPlant(Long plantId) {
        return allPlantsAccess || plantIds.contains(plantId);
    }

    /** true if this user holds every permission in {@code required}. */
    public boolean hasAllPermissions(Collection<Permission> required) {
        return permissions.containsAll(required);
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public boolean isEnabled() {
        return active;
    }
}
