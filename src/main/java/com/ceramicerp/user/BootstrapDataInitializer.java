package com.ceramicerp.user;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

/**
 * Runs once at every application start:
 * 1. Makes sure the "Super Admin" role exists and has EVERY permission (including ones added
 *    by new modules since the last start).
 * 2. If there are no users at all (fresh database), creates the first admin from
 *    app.bootstrap.* in config/application-local.yml.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BootstrapDataInitializer implements ApplicationRunner {

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final BootstrapProperties props;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Role superAdmin = ensureSuperAdminRole();
        if (userRepository.count() == 0) {
            createFirstAdmin(superAdmin);
        }
    }

    private Role ensureSuperAdminRole() {
        Role role = roleRepository.findByName(Role.SUPER_ADMIN).orElseGet(() -> {
            Role r = new Role();
            r.setName(Role.SUPER_ADMIN);
            r.setDescription("Full access to everything. Cannot be edited or deleted.");
            r.setSystemRole(true);
            return r;
        });
        Set<Permission> all = EnumSet.allOf(Permission.class);
        if (role.getId() == null || !role.getPermissions().equals(all)) {
            role.getPermissions().clear();
            role.getPermissions().addAll(all);
            role = roleRepository.save(role);
            log.info("Super Admin role synced with {} permissions", all.size());
        }
        return role;
    }

    private void createFirstAdmin(Role superAdmin) {
        String username = props.adminUsername();
        String password = props.adminPassword();
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            throw new IllegalStateException("No users exist yet. Set app.bootstrap.admin-username and "
                    + "app.bootstrap.admin-password in config/application-local.yml to create the first admin.");
        }
        User admin = new User();
        admin.setUsername(username.trim().toLowerCase(Locale.ROOT));
        admin.setFullName(props.adminFullName() == null ? "Administrator" : props.adminFullName());
        admin.setPasswordHash(passwordEncoder.encode(password));
        admin.setMustChangePassword(true);
        admin.setAllPlantsAccess(true);
        admin.getRoles().add(superAdmin);
        userRepository.save(admin);
        log.warn("Created first admin user '{}'. Change the password after first login.", admin.getUsername());
    }
}
