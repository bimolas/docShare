package com.subject77.docshare.config.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.lang.NonNull;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Converts Keycloak JWT claims (realm_access and resource_access roles)
 * into Spring Security GrantedAuthority objects.
 */
public class KeycloakJwtGrantedAuthoritiesConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    private static final String REALM_ACCESS = "realm_access";
    private static final String RESOURCE_ACCESS = "resource_access";
    private static final String ROLES = "roles";
    private static final String ROLE_PREFIX = "ROLE_";

    @Override
    public Collection<GrantedAuthority> convert(@NonNull Jwt jwt) {
        Set<GrantedAuthority> authorities = new HashSet<>();

        // 1. Extract Realm Roles
        Map<String, Object> realmAccess = jwt.getClaimAsMap(REALM_ACCESS);
        if (realmAccess != null && realmAccess.containsKey(ROLES)) {
            Object rolesObject = realmAccess.get(ROLES);
            if (rolesObject instanceof List<?> roles) {
                for (Object role : roles) {
                    if (role instanceof String roleName) {
                        addRoleAuthority(authorities, roleName);
                    }
                }
            }
        }

        // 2. Extract Client Roles (resource_access.<client_id>.roles)
        Map<String, Object> resourceAccess = jwt.getClaimAsMap(RESOURCE_ACCESS);
        if (resourceAccess != null) {
            for (Map.Entry<String, Object> entry : resourceAccess.entrySet()) {
                if (entry.getValue() instanceof Map<?, ?> clientAccess) {
                    Object rolesObject = clientAccess.get(ROLES);
                    if (rolesObject instanceof List<?> roles) {
                        for (Object role : roles) {
                            if (role instanceof String roleName) {
                                addRoleAuthority(authorities, roleName);
                            }
                        }
                    }
                }
            }
        }

        return List.copyOf(authorities);
    }

    private void addRoleAuthority(Set<GrantedAuthority> authorities, String roleName) {
        if (roleName == null || roleName.isBlank()) {
            return;
        }
        String cleanRole = roleName.trim();
        String formattedRole = cleanRole.startsWith(ROLE_PREFIX) ? cleanRole : ROLE_PREFIX + cleanRole;

        authorities.add(new SimpleGrantedAuthority(formattedRole));
        authorities.add(new SimpleGrantedAuthority(formattedRole.toUpperCase(Locale.ROOT)));

        if (cleanRole.startsWith(ROLE_PREFIX)) {
            authorities.add(new SimpleGrantedAuthority(cleanRole.substring(ROLE_PREFIX.length())));
        } else {
            authorities.add(new SimpleGrantedAuthority(cleanRole));
        }
    }
}
