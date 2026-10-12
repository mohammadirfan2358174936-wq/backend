package com.example.eventmanagement.service;

import com.example.eventmanagement.dto.request.LoginRequest;
import com.example.eventmanagement.dto.request.RegisterUserRequest;
import com.example.eventmanagement.dto.response.AuthResponse;
import com.example.eventmanagement.dto.response.UserResponse;
import com.example.eventmanagement.entity.Role;
import com.example.eventmanagement.entity.User;
import com.example.eventmanagement.exception.BadCredentialsException;
import com.example.eventmanagement.exception.EmailAlreadyExistsException;
import com.example.eventmanagement.exception.UserNotFoundException;
import com.example.eventmanagement.mapper.DtoMapper;
import com.example.eventmanagement.repository.UserRepository;
import com.example.eventmanagement.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * AUTHENTICATION
 * ==============
 * Registration, login and token issuing.
 *
 * Security posture:
 *  - BCrypt (strength 12) for hashing — never reversible.
 *  - Login failures are indistinguishable at the API: the same
 *    `BAD_CREDENTIALS`/401 whether the email exists or not, so the endpoint
 *    cannot be used to enumerate accounts.
 *  - Brute-force guard: after `max-login-attempts` failures the account is
 *    locked for `lock-duration-minutes`. The counter resets on success.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final DtoMapper mapper;

    @Value("${app.security.max-login-attempts:5}")
    private int maxAttempts;

    @Value("${app.security.lock-duration-minutes:15}")
    private int lockMinutes;

    /* ------------------------------------------------------------- register */

    @Transactional
    public AuthResponse register(RegisterUserRequest request) {
        String email = request.email().trim().toLowerCase();

        if (userRepository.existsByEmail(email)) {
            throw new EmailAlreadyExistsException(email);
        }

        Role role = roleFrom(request.role());

        User user = User.builder()
            .name(request.name().trim())
            .email(email)
            .passwordHash(passwordEncoder.encode(request.password()))
            .phone(request.phone())
            .role(role)
            .enabled(true)
            .build();

        User saved = userRepository.save(user);
        log.info("Registered new {} account: {}", role, email);

        return issue(saved);
    }

    /**
     * Only `ORGANIZER` and `ATTENDEE` may be self-assigned. `ADMIN` is seeded
     * (see `DataSeeder`) and never accepted from a request body.
     */
    private Role roleFrom(String requested) {
        if (requested == null || requested.isBlank()) {
            return Role.ATTENDEE;
        }
        try {
            Role role = Role.valueOf(requested.trim().toUpperCase());
            return role == Role.ADMIN ? Role.ATTENDEE : role;
        } catch (IllegalArgumentException invalid) {
            return Role.ATTENDEE;
        }
    }

    /* ---------------------------------------------------------------- login */

    @Transactional
    public AuthResponse login(LoginRequest request) {
        String email = request.email().trim().toLowerCase();

        User user = userRepository.findByEmail(email)
            .orElseThrow(BadCredentialsException::new);

        // Locked accounts fail with the same generic error.
        if (!user.isAccountNonLocked()) {
            log.warn("Login attempt on locked account {}", email);
            throw new BadCredentialsException();
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            registerFailure(user);
            throw new BadCredentialsException();
        }

        resetFailures(user);
        log.info("Successful login for {}", email);
        return issue(user);
    }

    private void registerFailure(User user) {
        user.setFailedLoginAttempts(user.getFailedLoginAttempts() + 1);
        if (user.getFailedLoginAttempts() >= maxAttempts) {
            user.setAccountLocked(true);
            user.setLockedUntil(Instant.now().plus(lockMinutes, ChronoUnit.MINUTES));
            log.warn("Account {} locked after {} failed attempts", user.getEmail(), maxAttempts);
        }
        userRepository.save(user);
    }

    private void resetFailures(User user) {
        if (user.getFailedLoginAttempts() > 0) {
            user.setFailedLoginAttempts(0);
            user.setAccountLocked(false);
            user.setLockedUntil(null);
            userRepository.save(user);
        }
    }

    /* ---------------------------------------------------------------- tokens */

    /** Mint the access token and pair it with the public profile. */
    public AuthResponse issue(User user) {
        String token = jwtService.generateToken(user);
        UserResponse profile = mapper.toUserResponse(user);
        return new AuthResponse(token, "Bearer", jwtService.getExpirationMs(), profile);
    }

    /* ----------------------------------------------------------------- misc */

    @Transactional(readOnly = true)
    public UserResponse me(String email) {
        return mapper.toUserResponse(findByEmail(email));
    }

    @Transactional(readOnly = true)
    public User requireByEmail(String email) {
        return findByEmail(email);
    }

    private User findByEmail(String email) {
        return userRepository.findByEmail(email)
            .orElseThrow(() -> new UserNotFoundException(email));
    }

    /** Stateless logout — the client discards the token. Logged for auditing. */
    @Transactional
    public void logout(String email) {
        if (email != null) {
            log.info("Logout for {}", email);
        }
    }
}
