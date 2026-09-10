package com.subject77.docshare.modules.organization.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.subject77.docshare.config.security.CurrentUserArgumentResolver;
import com.subject77.docshare.modules.organization.dto.OrganizationMemberDetailResponseDto;
import com.subject77.docshare.modules.organization.dto.OrganizationMemberResponseDto;
import com.subject77.docshare.modules.organization.dto.TransferOrgAdminRequestDto;
import com.subject77.docshare.modules.organization.entities.MembershipStatus;
import com.subject77.docshare.modules.organization.services.OrganizationService;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class OrganizationControllerTest {

    private MockMvc mockMvc;

    @Mock
    private OrganizationService organizationService;

    @Mock
    private UserService userService;

    @Mock
    private ObjectProvider<UserService> userServiceProvider;

    @InjectMocks
    private OrganizationController organizationController;

    private UserEntity mockUser;
    private static final String USER_ID = "user-123";
    private static final String ORG_CODE = "ACME";

    @BeforeEach
    void setUp() {
        mockUser = UserEntity.builder()
                .id(USER_ID)
                .email("user@acme.com")
                .firstName("John")
                .lastName("Doe")
                .role(Role.ROLE_USER)
                .build();

        org.mockito.Mockito.lenient().when(userServiceProvider.getIfAvailable()).thenReturn(userService);
        CurrentUserArgumentResolver resolver = new CurrentUserArgumentResolver(userServiceProvider);

        mockMvc = MockMvcBuilders.standaloneSetup(organizationController)
                .setCustomArgumentResolvers(resolver, new PageableHandlerMethodArgumentResolver())
                .setControllerAdvice(new com.subject77.docshare.exceptions.GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("GET /api/v1/organizations/{orgCode}/members should return paginated list of organization members")
    void getMembers_ShouldReturnPaginatedMembers() throws Exception {
        Jwt jwt = createJwt();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));

        OrganizationMemberResponseDto memberDto = OrganizationMemberResponseDto.builder()
                .userId(USER_ID)
                .email("user@acme.com")
                .firstName("John")
                .lastName("Doe")
                .role(Role.ROLE_ORG_ADMIN)
                .joinedAt(Instant.now())
                .build();

        Page<OrganizationMemberResponseDto> pageResponse = new PageImpl<>(List.of(memberDto), PageRequest.of(0, 20), 1);

        when(userService.getOrCreateCurrentUser(any(Jwt.class))).thenReturn(mockUser);
        when(organizationService.getOrganizationMembers(eq(ORG_CODE), any(Pageable.class), any(UserEntity.class)))
                .thenReturn(pageResponse);

        mockMvc.perform(get("/api/v1/organizations/{orgCode}/members", ORG_CODE)
                        .param("page", "0")
                        .param("size", "20")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].userId").value(USER_ID))
                .andExpect(jsonPath("$.content[0].email").value("user@acme.com"))
                .andExpect(jsonPath("$.content[0].role").value("ROLE_ORG_ADMIN"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.size").value(20));
    }

    @Test
    @DisplayName("POST /api/v1/organizations/{orgCode}/transfer-admin should return 200 with updated member details")
    void transferOrgAdmin_ShouldReturnOk() throws Exception {
        Jwt jwt = createJwt();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));

        OrganizationMemberDetailResponseDto responseDto = OrganizationMemberDetailResponseDto.builder()
                .userId("target-456")
                .email("target@acme.com")
                .orgCode(ORG_CODE)
                .role(Role.ROLE_ORG_ADMIN)
                .status(MembershipStatus.ACTIVE)
                .joinedAt(Instant.now())
                .build();

        when(userService.getOrCreateCurrentUser(any(Jwt.class))).thenReturn(mockUser);
        when(organizationService.transferOrgAdmin(eq(ORG_CODE), eq("target-456"), any(UserEntity.class)))
                .thenReturn(responseDto);

        TransferOrgAdminRequestDto request = TransferOrgAdminRequestDto.builder()
                .newAdminUserId("target-456")
                .build();

        mockMvc.perform(post("/api/v1/organizations/{orgCode}/transfer-admin", ORG_CODE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new ObjectMapper().writeValueAsString(request))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("target-456"))
                .andExpect(jsonPath("$.role").value("ROLE_ORG_ADMIN"))
                .andExpect(jsonPath("$.orgCode").value(ORG_CODE));
    }

    private Jwt createJwt() {
        return new Jwt(
                "token-string",
                Instant.now(),
                Instant.now().plusSeconds(3600),
                Map.of("alg", "RS256"),
                Map.of("sub", USER_ID, "email", "user@acme.com")
        );
    }
}
