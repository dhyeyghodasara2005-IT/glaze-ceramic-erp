package com.ceramicerp.auth;

import com.ceramicerp.auth.dto.ChangePasswordRequest;
import com.ceramicerp.auth.dto.CurrentUserResponse;
import com.ceramicerp.auth.dto.LoginRequest;
import com.ceramicerp.auth.dto.LoginResponse;
import com.ceramicerp.auth.dto.PreferencesRequest;
import com.ceramicerp.common.exception.BusinessException;
import com.ceramicerp.common.exception.NotFoundException;
import com.ceramicerp.organization.Plant;
import com.ceramicerp.organization.PlantRepository;
import com.ceramicerp.security.AppUserDetails;
import com.ceramicerp.security.CurrentUser;
import com.ceramicerp.security.JwtService;
import com.ceramicerp.user.Permission;
import com.ceramicerp.user.Role;
import com.ceramicerp.user.User;
import com.ceramicerp.user.UserRepository;
import com.ceramicerp.user.dto.PlantRef;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final PlantRepository plantRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * Checks username + password (Spring compares the BCrypt hash for us), then issues a token.
     * Wrong password -> BadCredentialsException -> 401. Deactivated -> DisabledException -> 401.
     */
    @Transactional
    public LoginResponse login(LoginRequest req) {
        String username = req.username().trim();
        Authentication auth;
        try {
            auth = authenticate(username, req.password());
        } catch (BadCredentialsException e) {
            // A password copied out of a PDF or a chat often brings an invisible space or line break with it. No
            // password may start or end with one (UserRules.PASSWORD_ENDS), so try once more without them. The exact
            // text is tried first, so an older password that does end in a space still works.
            String stripped = req.password().strip();
            if (stripped.isEmpty() || stripped.equals(req.password())) {
                throw e;
            }
            auth = authenticate(username, stripped);
        }
        AppUserDetails principal = (AppUserDetails) auth.getPrincipal();

        userRepository.updateLastLoginAt(principal.getUserId(), Instant.now());
        JwtService.IssuedToken token = jwtService.issue(principal);
        return new LoginResponse(token.token(), token.expiresAt(), currentUser(findUser(principal.getUserId())));
    }

    private Authentication authenticate(String username, String password) {
        return authenticationManager.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(username, password));
    }

    @Transactional(readOnly = true)
    public CurrentUserResponse me() {
        return currentUser(findUser(CurrentUser.get().getUserId()));
    }

    @Transactional
    public CurrentUserResponse updatePreferences(PreferencesRequest req) {
        User user = findUser(CurrentUser.get().getUserId());
        user.setPreferredLanguage(req.preferredLanguage());
        return currentUser(user);
    }

    @Transactional
    public void changePassword(ChangePasswordRequest req) {
        User user = findUser(CurrentUser.get().getUserId());
        // Same forgiveness as login: the current password may come with a space or line break at an end.
        if (!passwordEncoder.matches(req.currentPassword(), user.getPasswordHash())
                && !passwordEncoder.matches(req.currentPassword().strip(), user.getPasswordHash())) {
            throw new BusinessException("Current password is incorrect");
        }
        if (req.currentPassword().strip().equals(req.newPassword())) {
            throw new BusinessException("New password must be different from the current one");
        }
        user.setPasswordHash(passwordEncoder.encode(req.newPassword()));
        user.setMustChangePassword(false);
    }

    private User findUser(Long id) {
        return userRepository.findById(id).orElseThrow(() -> new NotFoundException("User", id));
    }

    private CurrentUserResponse currentUser(User user) {
        Collection<Plant> plants = user.isAllPlantsAccess() ? plantRepository.findAllByOrderByNameAsc() : user.getPlants();
        List<PlantRef> plantRefs = plants.stream()
                .filter(Plant::isActive)
                .map(PlantRef::from)
                .sorted(Comparator.comparing(PlantRef::name))
                .toList();
        return new CurrentUserResponse(
                user.getId(),
                user.getUsername(),
                user.getFullName(),
                user.getPreferredLanguage(),
                user.isMustChangePassword(),
                user.isAllPlantsAccess(),
                user.getRoles().stream().map(Role::getName).sorted().toList(),
                user.permissions().stream().map(Permission::name).sorted().toList(),
                plantRefs);
    }
}
