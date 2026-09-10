package com.subject77.docshare.config.security;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/test")
public class SecurityTestController {

    @GetMapping("/public")
    public ResponseEntity<Map<String, String>> publicEndpoint() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "message", "Public endpoint accessible without authentication"
        ));
    }

    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> authenticatedUserInfo(
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication
    ) {
        Map<String, Object> userInfo = new HashMap<>();
        if (jwt != null) {
            userInfo.put("subject_id", jwt.getSubject());
            userInfo.put("email", jwt.getClaimAsString("email"));
            userInfo.put("username", jwt.getClaimAsString("preferred_username"));
            userInfo.put("firstName", jwt.getClaimAsString("given_name"));
            userInfo.put("lastName", jwt.getClaimAsString("family_name"));
            userInfo.put("issuer", jwt.getIssuer());
            userInfo.put("claims", jwt.getClaims());
        }

        if (authentication != null) {
            userInfo.put("authorities", authentication.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .collect(Collectors.toList()));
        }

        return ResponseEntity.ok(userInfo);
    }

    @GetMapping("/user")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Map<String, String>> userEndpoint() {
        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "message", "User endpoint accessed successfully with ROLE_USER"
        ));
    }

    @GetMapping("/org-admin")
    @PreAuthorize("hasRole('ORG_ADMIN')")
    public ResponseEntity<Map<String, String>> orgAdminEndpoint() {
        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "message", "Org Admin endpoint accessed successfully with ROLE_ORG_ADMIN"
        ));
    }

    @GetMapping("/admin")
    @PreAuthorize("hasRole('ADMIN') or hasRole('ORG_ADMIN')")
    public ResponseEntity<Map<String, String>> adminEndpoint() {
        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "message", "Admin endpoint accessed successfully with ROLE_ADMIN or ROLE_ORG_ADMIN"
        ));
    }
}
