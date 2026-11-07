package com.adags.modules.identity.api;

import com.adags.modules.identity.application.AuthenticationService;
import com.adags.modules.identity.application.LoginResult;
import com.adags.shared.security.InvalidTokenException;
import com.adags.shared.security.IssuedSession;
import com.adags.shared.security.RefreshCookieFactory;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private static final String REFRESH_COOKIE_NAME = "adags_refresh";

    private final AuthenticationService authenticationService;
    private final RefreshCookieFactory refreshCookieFactory;

    public AuthController(AuthenticationService authenticationService, RefreshCookieFactory refreshCookieFactory) {
        this.authenticationService = authenticationService;
        this.refreshCookieFactory = refreshCookieFactory;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        LoginResult result = authenticationService.login(request.username(), request.password());
        IssuedSession session = result.session();
        ResponseCookie cookie = refreshCookieFactory.build(session.refreshToken(), session.refreshTokenExpiresAt(), httpRequest.isSecure());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(new LoginResponse(session.userId(), session.accessToken(), session.accessTokenExpiresAt(), result.mustChangePassword()));
    }

    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(
            @CookieValue(name = REFRESH_COOKIE_NAME, required = false) String refreshToken,
            HttpServletRequest httpRequest) {
        if (refreshToken == null) {
            throw new InvalidTokenException();
        }
        IssuedSession session = authenticationService.refresh(refreshToken);
        ResponseCookie cookie = refreshCookieFactory.build(session.refreshToken(), session.refreshTokenExpiresAt(), httpRequest.isSecure());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(new TokenResponse(session.userId(), session.accessToken(), session.accessTokenExpiresAt()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
            @CookieValue(name = REFRESH_COOKIE_NAME, required = false) String refreshToken,
            HttpServletRequest httpRequest) {
        String accessToken = extractBearerToken(authorizationHeader);
        authenticationService.logout(accessToken, refreshToken);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, refreshCookieFactory.expired(httpRequest.isSecure()).toString())
                .build();
    }

    private String extractBearerToken(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            return null;
        }
        return authorizationHeader.substring("Bearer ".length());
    }
}
