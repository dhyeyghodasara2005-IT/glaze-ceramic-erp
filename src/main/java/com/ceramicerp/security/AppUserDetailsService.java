package com.ceramicerp.security;

import com.ceramicerp.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Loads a user from the database for Spring Security. Used at login AND on every request
 * with a token, so deactivating a user or changing their role takes effect immediately.
 */
@Service
@RequiredArgsConstructor
public class AppUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public AppUserDetails loadUserByUsername(String username) {
        return userRepository.findByUsernameIgnoreCase(username)
                .map(AppUserDetails::from)
                .orElseThrow(() -> new UsernameNotFoundException(username));
    }
}
