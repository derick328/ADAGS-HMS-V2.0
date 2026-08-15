package com.adags.shared.security;

import com.adags.shared.authz.Actor;
import org.springframework.security.authentication.AbstractAuthenticationToken;

import java.util.List;

public class ActorAuthenticationToken extends AbstractAuthenticationToken {

    private final Actor actor;

    public ActorAuthenticationToken(Actor actor) {
        super(List.of());
        this.actor = actor;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public Object getPrincipal() {
        return actor;
    }
}
