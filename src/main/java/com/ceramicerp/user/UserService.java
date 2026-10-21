package com.ceramicerp.user;

import com.ceramicerp.common.PageResponse;
import com.ceramicerp.common.Text;
import com.ceramicerp.common.exception.BusinessException;
import com.ceramicerp.common.exception.ForbiddenException;
import com.ceramicerp.common.exception.NotFoundException;
import com.ceramicerp.organization.Plant;
import com.ceramicerp.organization.PlantRepository;
import com.ceramicerp.security.AppUserDetails;
import com.ceramicerp.security.CurrentUser;
import com.ceramicerp.user.dto.ResetPasswordRequest;
import com.ceramicerp.user.dto.UserCreateRequest;
import com.ceramicerp.user.dto.UserResponse;
import com.ceramicerp.user.dto.UserUpdateRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * User management rules:
 * - Users are deactivated, never deleted (history must keep pointing at them).
 * - You can only manage users who are not more powerful than you, and only give roles
 *   whose permissions you already have (no privilege escalation).
 * - At least one active Super Admin must always exist.
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PlantRepository plantRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public PageResponse<UserResponse> search(String search, Boolean active, int page, int size) {
        String pattern = Text.trimToNull(search) == null
                ? null
                : "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
        var pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), Sort.by("fullName"));
        return PageResponse.of(userRepository.search(pattern, active, pageable), UserResponse::from);
    }

    @Transactional(readOnly = true)
    public UserResponse get(Long id) {
        return UserResponse.from(find(id));
    }

    @Transactional
    public UserResponse create(UserCreateRequest req) {
        String username = req.username().trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByUsernameIgnoreCase(username)) {
            throw new BusinessException("Username already taken: " + username);
        }
        Set<Role> roles = loadRoles(req.roleIds());
        assertCanAssign(roles, req.allPlantsAccess());

        User user = new User();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(req.password()));
        user.setMustChangePassword(true);
        applyProfile(user, req.fullName(), req.phone(), req.email(), req.preferredLanguage());
        user.setRoles(roles);
        user.setAllPlantsAccess(req.allPlantsAccess());
        user.setPlants(loadPlants(req.plantIds()));
        return UserResponse.from(userRepository.save(user));
    }

    @Transactional
    public UserResponse update(Long id, UserUpdateRequest req) {
        User user = find(id);
        assertCanManage(user);
        Set<Role> roles = loadRoles(req.roleIds());
        assertCanAssign(roles, req.allPlantsAccess());

        boolean losesSuperAdmin = user.hasRole(Role.SUPER_ADMIN)
                && roles.stream().noneMatch(r -> r.getName().equals(Role.SUPER_ADMIN));
        if (losesSuperAdmin && user.isActive()) {
            assertNotLastSuperAdmin();
        }

        applyProfile(user, req.fullName(), req.phone(), req.email(), req.preferredLanguage());
        user.setRoles(roles);
        user.setAllPlantsAccess(req.allPlantsAccess());
        user.setPlants(loadPlants(req.plantIds()));
        return UserResponse.from(user);
    }

    @Transactional
    public UserResponse deactivate(Long id) {
        User user = find(id);
        assertCanManage(user);
        if (user.getId().equals(CurrentUser.get().getUserId())) {
            throw new BusinessException("You cannot deactivate your own account");
        }
        if (user.isActive() && user.hasRole(Role.SUPER_ADMIN)) {
            assertNotLastSuperAdmin();
        }
        user.setActive(false);
        return UserResponse.from(user);
    }

    @Transactional
    public UserResponse activate(Long id) {
        User user = find(id);
        assertCanManage(user);
        user.setActive(true);
        return UserResponse.from(user);
    }

    @Transactional
    public UserResponse resetPassword(Long id, ResetPasswordRequest req) {
        User user = find(id);
        assertCanManage(user);
        user.setPasswordHash(passwordEncoder.encode(req.newPassword()));
        user.setMustChangePassword(true);
        return UserResponse.from(user);
    }

    private User find(Long id) {
        return userRepository.findById(id).orElseThrow(() -> new NotFoundException("User", id));
    }

    private void applyProfile(User user, String fullName, String phone, String email, String language) {
        user.setFullName(fullName.trim());
        user.setPhone(Text.trimToNull(phone));
        user.setEmail(Text.trimToNull(email));
        user.setPreferredLanguage(language == null ? "en" : language);
    }

    private Set<Role> loadRoles(Set<Long> roleIds) {
        List<Role> roles = roleRepository.findAllById(roleIds);
        if (roles.size() != roleIds.size()) {
            throw new BusinessException("One or more selected roles do not exist");
        }
        return new HashSet<>(roles);
    }

    private Set<Plant> loadPlants(Set<Long> plantIds) {
        if (plantIds == null || plantIds.isEmpty()) {
            return new HashSet<>();
        }
        List<Plant> plants = plantRepository.findAllById(plantIds);
        if (plants.size() != plantIds.size()) {
            throw new BusinessException("One or more selected plants do not exist");
        }
        return new HashSet<>(plants);
    }

    /** You may only give roles whose permissions you already hold. */
    private void assertCanAssign(Set<Role> roles, boolean allPlantsAccess) {
        AppUserDetails me = CurrentUser.get();
        roles.forEach(r -> {
            if (!me.hasAllPermissions(r.getPermissions())) {
                throw new ForbiddenException("You cannot assign role '" + r.getName()
                        + "' because it has permissions you do not have");
            }
        });
        if (allPlantsAccess && !me.isAllPlantsAccess()) {
            throw new ForbiddenException("You cannot give all-plants access because you do not have it");
        }
    }

    /** You may only edit/reset/deactivate users who are not more powerful than you. */
    private void assertCanManage(User target) {
        if (!CurrentUser.get().hasAllPermissions(target.permissions())) {
            throw new ForbiddenException("You cannot manage a user who has more permissions than you");
        }
    }

    private void assertNotLastSuperAdmin() {
        Role superAdmin = roleRepository.findByName(Role.SUPER_ADMIN)
                .orElseThrow(() -> new IllegalStateException("Super Admin role missing"));
        if (userRepository.countByRoles_IdAndActiveTrue(superAdmin.getId()) <= 1) {
            throw new BusinessException("At least one active Super Admin must remain");
        }
    }
}
