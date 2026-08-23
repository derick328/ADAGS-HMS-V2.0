package com.adags.modules.identity.domain;

public class DuplicateUsernameException extends RuntimeException {

    public DuplicateUsernameException(String username) {
        super("A user with username '" + username + "' already exists.");
    }
}
