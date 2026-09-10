package com.subject77.docshare.config.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

class KeycloakJwtGrantedAuthoritiesConverterTest {

    private KeycloakJwtGrantedAuthoritiesConverter converter;

    @BeforeEach
    void setUp() {
        converter = new KeycloakJwtGrantedAuthoritiesConverter();
    }

    @Test
    @DisplayName("Should extract realm roles correctly and map with ROLE_ prefix")
    void convert_ShouldExtractRealmRoles() {
        Map<String, Object> realmAccess = Map.of(
                "roles", List.of("ROLE_USER", "default-roles-docshare", "offline_access")
        );

        Jwt jwt = createJwt(Map.of("realm_access", realmAccess));

        Collection<GrantedAuthority> authorities = converter.convert(jwt);

        assertThat(authorities).isNotNull();
        List<String> authorityNames = authorities.stream().map(GrantedAuthority::getAuthority).toList();

        assertThat(authorityNames).contains("ROLE_USER", "USER", "ROLE_DEFAULT-ROLES-DOCSHARE");
    }

    @Test
    @DisplayName("Should extract client resource_access roles correctly")
    void convert_ShouldExtractResourceAccessRoles() {
        Map<String, Object> accountClient = Map.of("roles", List.of("manage-account", "view-profile"));
        Map<String, Object> resourceAccess = Map.of("account", accountClient);

        Jwt jwt = createJwt(Map.of("resource_access", resourceAccess));

        Collection<GrantedAuthority> authorities = converter.convert(jwt);

        assertThat(authorities).isNotNull();
        List<String> authorityNames = authorities.stream().map(GrantedAuthority::getAuthority).toList();

        assertThat(authorityNames).contains("ROLE_MANAGE-ACCOUNT", "manage-account");
    }

    @Test
    @DisplayName("Should handle JWT with missing security claims gracefully")
    void convert_ShouldHandleMissingClaims() {
        Jwt jwt = createJwt(Collections.emptyMap());

        Collection<GrantedAuthority> authorities = converter.convert(jwt);

        assertThat(authorities).isEmpty();
    }

    private Jwt createJwt(Map<String, Object> claims) {
        Map<String, Object> headers = Map.of("alg", "RS256");
        Map<String, Object> fullClaims = new HashMap<>(claims);
        fullClaims.put("sub", "7128fe1a-90d5-43ea-b3c7-1ffe727e8a68");

        return new Jwt(
                "dummy-token-value",
                Instant.now(),
                Instant.now().plusSeconds(3600),
                headers,
                fullClaims
        );
    }
}
