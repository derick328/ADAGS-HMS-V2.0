package com.adags.modules.identity.application;

import com.adags.modules.identity.domain.IncorrectCurrentPasswordException;
import com.adags.modules.identity.domain.User;
import com.adags.modules.identity.domain.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;

// Admin-only self-service path for changing the bootstrap or current password.
@Service
public class ChangeOwnPasswordService {

    private static final int MIN_PASSWORD_LENGTH = 8;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public ChangeOwnPasswordService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public void changePassword(ChangeOwnPasswordCommand command) {
        User user = userRepository.findById(command.userId())
                .orElseThrow(() -> new NoSuchElementException("User not found: " + command.userId()));
        if (!passwordEncoder.matches(command.currentPassword(), user.passwordHash())) {
            throw new IncorrectCurrentPasswordException();
        }
        if (command.newPassword() == null || command.newPassword().length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalArgumentException("New password must be at least " + MIN_PASSWORD_LENGTH + " characters.");
        }
        String newHash = passwordEncoder.encode(command.newPassword());
        userRepository.updatePassword(user.id(), newHash, false);
    }
}
