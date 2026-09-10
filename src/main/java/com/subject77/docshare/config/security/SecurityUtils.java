package com.subject77.docshare.config.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Optional;

/**
 * Utility class for interacting with Spring Security's SecurityContextHolder.
 */
public final class SecurityUtils {

    private SecurityUtils() {
        // Private constructor to prevent instantiation
    }

    /**
     * Retrieve the active Jwt instance from SecurityContext.
     */
    public static Optional<Jwt> getCurrentJwt() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            return Optional.of(jwt);
        }
        return Optional.empty();
    }

    /**
     * Retrieve the current Keycloak User UUID (sub claim).
     */
    public static Optional<String> getCurrentKeycloakId() {
        return getCurrentJwt().map(Jwt::getSubject);
    }

    /**
     * Retrieve the email of the currently authenticated user.
     */
    public static Optional<String> getCurrentUserEmail() {
        return getCurrentJwt().map(jwt -> jwt.getClaimAsString("email"));
    }
}
