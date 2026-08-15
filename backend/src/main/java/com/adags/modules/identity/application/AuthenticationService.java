package com.adags.modules.identity.application;

import com.adags.modules.identity.domain.User;
import com.adags.modules.identity.domain.UserRepository;
import com.adags.shared.security.InvalidCredentialsException;
import com.adags.shared.security.IssuedSession;
import com.adags.shared.security.SessionStore;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@Service
public class AuthenticationService {

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final Duration LOCK_DURATION = Duration.ofMinutes(15);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final SessionStore sessionStore;
    private final Clock clock;

    public AuthenticationService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                                  SessionStore sessionStore, Clock clock) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.sessionStore = sessionStore;
        this.clock = clock;
    }

    @Transactional
    public LoginResult login(String username, String rawPassword) {
        User user = userRepository.findByUsername(username).orElseThrow(InvalidCredentialsException::new);
        Instant now = clock.instant();

        // Same generic failure for "no such user", "wrong password", and "locked
        // out" — telling an attacker which one it is is a free username-enumeration
        // and lockout oracle.
        if (!user.isActive() || user.isLocked(now)) {
            throw new InvalidCredentialsException();
        }

        if (!passwordEncoder.matches(rawPassword, user.passwordHash())) {
            int attempts = user.failedAttempts() + 1;
            Instant lockedUntil = attempts >= MAX_FAILED_ATTEMPTS ? now.plus(LOCK_DURATION) : null;
            userRepository.recordLoginFailure(user.id(), attempts, lockedUntil);
            throw new InvalidCredentialsException();
        }

        userRepository.recordLoginSuccess(user.id(), now);
        IssuedSession session = sessionStore.issueSession(user.id());
        return new LoginResult(session, user.mustChangePassword());
    }

    public IssuedSession refresh(String refreshToken) {
        return sessionStore.rotateRefreshToken(refreshToken);
    }

    public void logout(String accessToken, String refreshToken) {
        sessionStore.revoke(accessToken, refreshToken);
    }
}
