package com.adags.modules.identity.application;

import com.adags.modules.identity.domain.DuplicateUsernameException;
import com.adags.modules.identity.domain.User;
import com.adags.modules.identity.domain.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 18-admin.md §4.2: onboarding creates the login account. must_change_password
// is always TRUE regardless of what the admin types as the temporary password
// — same "no default password" discipline as V3__BootstrapAdmin, just with an
// admin as the install-time secret's replacement.
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
        if (command.temporaryPassword() == null || command.temporaryPassword().length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalArgumentException("Temporary password must be at least " + MIN_PASSWORD_LENGTH + " characters.");
        }
        if (userRepository.findByUsername(username).isPresent()) {
            throw new DuplicateUsernameException(username);
        }
        String passwordHash = passwordEncoder.encode(command.temporaryPassword());
        return userRepository.insert(username, passwordHash, command.createdBy());
    }
}
