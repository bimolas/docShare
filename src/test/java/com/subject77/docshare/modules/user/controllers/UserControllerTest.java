package com.subject77.docshare.modules.user.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.subject77.docshare.config.security.CurrentUserArgumentResolver;
import com.subject77.docshare.modules.user.dto.UserRequestDto;
import com.subject77.docshare.modules.user.dto.UserResponseDto;
import com.subject77.docshare.modules.user.entities.Role;
import com.subject77.docshare.modules.user.entities.UserEntity;
import com.subject77.docshare.modules.user.services.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    private MockMvc mockMvc;

    @Mock
    private UserService userService;

    @Mock
    private ObjectProvider<UserService> userServiceProvider;

    @InjectMocks
    private UserController userController;

    private ObjectMapper objectMapper;
    private UserEntity mockUser;
    private UserResponseDto userResponseDto;

    private static final String KEYCLOAK_ID = "7128fe1a-90d5-43ea-b3c7-1ffe727e8a68";

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();

        mockUser = UserEntity.builder()
                .id(KEYCLOAK_ID)
                .email("miko@gmail.com")
                .firstName("miko")
                .lastName("miko")
                .role(Role.ROLE_USER)
                .build();

        userResponseDto = UserResponseDto.builder()
                .id(KEYCLOAK_ID)
                .email("miko@gmail.com")
                .firstName("miko")
                .lastName("miko")
                .role(Role.ROLE_USER)
                .build();

        org.mockito.Mockito.lenient().when(userServiceProvider.getIfAvailable()).thenReturn(userService);
        CurrentUserArgumentResolver resolver = new CurrentUserArgumentResolver(userServiceProvider);

        mockMvc = MockMvcBuilders.standaloneSetup(userController)
                .setCustomArgumentResolvers(resolver)
                .setControllerAdvice(new com.subject77.docshare.exceptions.GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("GET /api/v1/users/me should return current user profile")
    void getMyProfile_ShouldReturnUserProfile() throws Exception {
        Jwt jwt = createJwt();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));

        when(userService.getOrCreateCurrentUser(any(Jwt.class))).thenReturn(mockUser);
        when(userService.getCurrentUserProfile(any(UserEntity.class))).thenReturn(userResponseDto);

        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(KEYCLOAK_ID))
                .andExpect(jsonPath("$.email").value("miko@gmail.com"))
                .andExpect(jsonPath("$.firstName").value("miko"))
                .andExpect(jsonPath("$.role").value("ROLE_USER"));
    }

    @Test
    @DisplayName("PUT /api/v1/users/me should update and return modified user profile")
    void updateMyProfile_ShouldReturnUpdatedProfile() throws Exception {
        Jwt jwt = createJwt();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));

        UserRequestDto requestDto = UserRequestDto.builder()
                .firstName("UpdatedMiko")
                .lastName("UpdatedLastName")
                .build();

        UserResponseDto updatedResponse = UserResponseDto.builder()
                .id(KEYCLOAK_ID)
                .email("miko@gmail.com")
                .firstName("UpdatedMiko")
                .lastName("UpdatedLastName")
                .role(Role.ROLE_USER)
                .build();

        when(userService.getOrCreateCurrentUser(any(Jwt.class))).thenReturn(mockUser);
        when(userService.updateUserProfile(any(UserEntity.class), any(UserRequestDto.class))).thenReturn(updatedResponse);

        mockMvc.perform(put("/api/v1/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(KEYCLOAK_ID))
                .andExpect(jsonPath("$.firstName").value("UpdatedMiko"))
                .andExpect(jsonPath("$.lastName").value("UpdatedLastName"));
    }

    @Test
    @DisplayName("GET /api/v1/users/search?q=miko should return matching user list")
    void searchUsers_ShouldReturnUserList_WhenValidQueryProvided() throws Exception {
        Jwt jwt = createJwt();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));

        when(userService.getOrCreateCurrentUser(any(Jwt.class))).thenReturn(mockUser);
        when(userService.searchUsers(eq("miko"), any(UserEntity.class))).thenReturn(java.util.List.of(userResponseDto));

        mockMvc.perform(get("/api/v1/users/search")
                        .param("q", "miko"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(KEYCLOAK_ID))
                .andExpect(jsonPath("$[0].email").value("miko@gmail.com"))
                .andExpect(jsonPath("$[0].firstName").value("miko"));
    }

    @Test
    @DisplayName("GET /api/v1/users/search without query should return 400 Bad Request")
    void searchUsers_ShouldReturnBadRequest_WhenQueryIsBlank() throws Exception {
        Jwt jwt = createJwt();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));

        when(userService.getOrCreateCurrentUser(any(Jwt.class))).thenReturn(mockUser);
        when(userService.searchUsers(eq(null), any(UserEntity.class))).thenThrow(new IllegalArgumentException("Search query must not be blank"));

        mockMvc.perform(get("/api/v1/users/search"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Search query must not be blank"));
    }

    private Jwt createJwt() {
        return new Jwt(
                "token-string",
                Instant.now(),
                Instant.now().plusSeconds(3600),
                Map.of("alg", "RS256"),
                Map.of("sub", KEYCLOAK_ID, "email", "miko@gmail.com")
        );
    }
}
