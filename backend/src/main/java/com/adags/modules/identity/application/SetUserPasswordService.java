package com.adags.modules.identity.application;

import com.adags.modules.identity.domain.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class SetUserPasswordService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public SetUserPasswordService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public void setPassword(UUID userId, String password) {
        userRepository.findById(userId).orElseThrow(() -> new NoSuchElementException("User not found: " + userId));
        if (password == null || password.length() < 8) {
            throw new IllegalArgumentException("Password must be at least 8 characters.");
        }
        userRepository.updatePassword(userId, passwordEncoder.encode(password), false);
    }
}
