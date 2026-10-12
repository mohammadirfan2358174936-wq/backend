package com.example.eventmanagement.security;

import com.example.eventmanagement.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bridges Spring Security to the `User` entity.
 *
 * Only the JWT filter uses this, and it is the *only* place a DB lookup happens
 * on an authenticated request — which is why `JwtService` carries the role in
 * the token: for `isTokenValid` we already hold the full `UserDetails`.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return userRepository.findByEmail(username)
            .orElseThrow(() -> new UsernameNotFoundException(
                "No account found for %s".formatted(username)));
    }
}
