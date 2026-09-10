package com.subject77.docshare.config.security;

import com.subject77.docshare.modules.user.entities.UserEntity;
import com.subject77.docshare.modules.user.services.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.MethodParameter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CurrentUserArgumentResolverTest {

    @Mock
    private ObjectProvider<UserService> userServiceProvider;

    @Mock
    private UserService userService;

    @Mock
    private MethodParameter methodParameter;

    @Mock
    private NativeWebRequest webRequest;

    @Mock
    private ModelAndViewContainer mavContainer;

    private CurrentUserArgumentResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new CurrentUserArgumentResolver(userServiceProvider);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Should return true when parameter is annotated with @CurrentUser")
    void supportsParameter_ShouldReturnTrue_WhenAnnotated() {
        when(methodParameter.hasParameterAnnotation(CurrentUser.class)).thenReturn(true);

        boolean supports = resolver.supportsParameter(methodParameter);

        assertThat(supports).isTrue();
    }

    @Test
    @DisplayName("Should resolve UserEntity parameter by calling UserService JIT lookup")
    void resolveArgument_ShouldResolveUserEntity() {
        Jwt jwt = createJwt();
        JwtAuthenticationToken authenticationToken = new JwtAuthenticationToken(jwt);
        SecurityContextHolder.getContext().setAuthentication(authenticationToken);

        doReturn(UserEntity.class).when(methodParameter).getParameterType();
        when(userServiceProvider.getIfAvailable()).thenReturn(userService);

        UserEntity mockUser = UserEntity.builder().id("7128fe1a-90d5-43ea-b3c7-1ffe727e8a68").email("miko@gmail.com").build();
        when(userService.getOrCreateCurrentUser(jwt)).thenReturn(mockUser);

        Object resolved = resolver.resolveArgument(methodParameter, mavContainer, webRequest, null);

        assertThat(resolved).isNotNull();
        assertThat(resolved).isInstanceOf(UserEntity.class);
        assertThat(((UserEntity) resolved).getEmail()).isEqualTo("miko@gmail.com");
    }

    @Test
    @DisplayName("Should resolve Jwt parameter directly without calling UserService")
    void resolveArgument_ShouldResolveJwtDirectly() {
        Jwt jwt = createJwt();
        JwtAuthenticationToken authenticationToken = new JwtAuthenticationToken(jwt);
        SecurityContextHolder.getContext().setAuthentication(authenticationToken);

        doReturn(Jwt.class).when(methodParameter).getParameterType();

        Object resolved = resolver.resolveArgument(methodParameter, mavContainer, webRequest, null);

        assertThat(resolved).isNotNull();
        assertThat(resolved).isSameAs(jwt);
        verifyNoInteractions(userServiceProvider);
    }

    @Test
    @DisplayName("Should return null when SecurityContext has no authentication")
    void resolveArgument_ShouldReturnNull_WhenUnauthenticated() {
        SecurityContextHolder.clearContext();

        Object resolved = resolver.resolveArgument(methodParameter, mavContainer, webRequest, null);

        assertThat(resolved).isNull();
    }

    @Test
    @DisplayName("Should return null when Principal is not a Jwt")
    void resolveArgument_ShouldReturnNull_WhenPrincipalNotJwt() {
        UsernamePasswordAuthenticationToken nonJwtAuth =
                new UsernamePasswordAuthenticationToken("user", "password", Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(nonJwtAuth);

        Object resolved = resolver.resolveArgument(methodParameter, mavContainer, webRequest, null);

        assertThat(resolved).isNull();
    }

    private Jwt createJwt() {
        return new Jwt(
                "token",
                Instant.now(),
                Instant.now().plusSeconds(3600),
                Map.of("alg", "RS256"),
                Map.of("sub", "7128fe1a-90d5-43ea-b3c7-1ffe727e8a68", "email", "miko@gmail.com")
        );
    }
}
