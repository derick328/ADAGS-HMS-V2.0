package com.adags.modules.identity.application;

import com.adags.modules.identity.domain.DuplicateUsernameException;
import com.adags.modules.identity.domain.User;
import com.adags.modules.identity.domain.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Admin-provided passwords are permanent until an admin changes them.
@Service
public class CreateUserService {

    private static final int MIN_PASSWORD_LENGTH = 8;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public CreateUserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public User create(CreateUserCommand command) {
        String username = command.username() == null ? "" : command.username().trim();
        if (username.isBlank()) {
            throw new IllegalArgumentException("Enter a username.");
        }
        if (command.password() == null || command.password().length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalArgumentException("Password must be at least " + MIN_PASSWORD_LENGTH + " characters.");
        }
        if (userRepository.findByUsername(username).isPresent()) {
            throw new DuplicateUsernameException(username);
        }
        String passwordHash = passwordEncoder.encode(command.password());
        return userRepository.insert(username, passwordHash, command.createdBy());
    }
}
