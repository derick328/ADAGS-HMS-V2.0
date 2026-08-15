package com.adags.shared.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    // OWASP-recommended Argon2id parameters baked into Spring Security's default factory.
    @Bean
    public PasswordEncoder passwordEncoder() {
        return Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    }

    @Bean
    public ActorResolvingFilter actorResolvingFilter(SessionStore sessionStore) {
        return new ActorResolvingFilter(sessionStore);
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, ActorResolvingFilter actorResolvingFilter) throws Exception {
        http
                // Stateless bearer tokens, not a browser session — csrf is mitigated on
                // the one cookie-authenticated endpoint (/auth/refresh) via SameSite=Strict
                // on the refresh cookie instead (see RefreshCookieFactory).
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/auth/login", "/auth/refresh").permitAll()
                        .requestMatchers("/v3/api-docs", "/v3/api-docs.yaml", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/actuator/health").permitAll()
                        .anyRequest().authenticated())
                // Without this, Spring Security has no registered login mechanism to
                // challenge with and falls back to 403 for "not authenticated at all" —
                // wrong for a bearer-token API, where that case is 401.
                .exceptionHandling(eh -> eh.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .addFilterBefore(actorResolvingFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
