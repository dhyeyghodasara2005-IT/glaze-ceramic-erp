package com.ceramicerp.user;

import com.ceramicerp.common.Text;
import com.ceramicerp.common.exception.BusinessException;
import com.ceramicerp.common.exception.ForbiddenException;
import com.ceramicerp.common.exception.NotFoundException;
import com.ceramicerp.security.CurrentUser;
import com.ceramicerp.user.dto.PermissionResponse;
import com.ceramicerp.user.dto.RoleRequest;
import com.ceramicerp.user.dto.RoleResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RoleService {

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;

    public List<PermissionResponse> listPermissions() {
        return Arrays.stream(Permission.values()).map(PermissionResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<RoleResponse> list() {
        return roleRepository.findAllByOrderByNameAsc().stream()
                .map(r -> RoleResponse.from(r, userRepository.countByRoles_Id(r.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public RoleResponse get(Long id) {
        Role role = find(id);
        return RoleResponse.from(role, userRepository.countByRoles_Id(id));
    }

    @Transactional
    public RoleResponse create(RoleRequest req) {
        String name = req.name().trim();
        if (roleRepository.existsByNameIgnoreCase(name)) {
            throw new BusinessException("A role with this name already exists: " + name);
        }
        assertCanGrant(req);
        Role role = new Role();
        apply(role, req, name);
        return RoleResponse.from(roleRepository.save(role), 0);
    }

    @Transactional
    public RoleResponse update(Long id, RoleRequest req) {
        Role role = find(id);
        if (role.isSystemRole()) {
            throw new BusinessException("System role '" + role.getName() + "' cannot be edited");
        }
        String name = req.name().trim();
        if (roleRepository.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw new BusinessException("A role with this name already exists: " + name);
        }
        assertCanGrant(req);
        apply(role, req, name);
        return RoleResponse.from(role, userRepository.countByRoles_Id(id));
    }

    @Transactional
    public void delete(Long id) {
        Role role = find(id);
        if (role.isSystemRole()) {
            throw new BusinessException("System role '" + role.getName() + "' cannot be deleted");
        }
        long users = userRepository.countByRoles_Id(id);
        if (users > 0) {
            throw new BusinessException("Role is assigned to " + users + " user(s). Remove it from them first.");
        }
        roleRepository.delete(role);
    }

    Role find(Long id) {
        return roleRepository.findById(id).orElseThrow(() -> new NotFoundException("Role", id));
    }

    /** Stops privilege escalation: you can't create a role more powerful than yourself. */
    private void assertCanGrant(RoleRequest req) {
        if (!CurrentUser.get().hasAllPermissions(req.permissions())) {
            throw new ForbiddenException("You cannot grant permissions that you do not have yourself");
        }
    }

    private void apply(Role role, RoleRequest req, String name) {
        role.setName(name);
        role.setDescription(Text.trimToNull(req.description()));
        role.getPermissions().clear();
        if (!req.permissions().isEmpty()) {
            role.getPermissions().addAll(EnumSet.copyOf(req.permissions()));
        }
    }
}
