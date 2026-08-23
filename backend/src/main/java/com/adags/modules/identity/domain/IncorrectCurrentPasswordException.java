package com.adags.modules.identity.domain;

public class IncorrectCurrentPasswordException extends RuntimeException {

    public IncorrectCurrentPasswordException() {
        super("Current password is incorrect.");
    }
}
