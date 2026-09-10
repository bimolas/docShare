package com.subject77.docshare.modules.user.services;

import com.subject77.docshare.exceptions.ResourceNotFoundException;
import com.subject77.docshare.modules.user.dto.UserRequestDto;
import com.subject77.docshare.modules.user.dto.UserResponseDto;
import com.subject77.docshare.modules.user.entities.Role;
import com.subject77.docshare.modules.user.entities.UserEntity;
import com.subject77.docshare.modules.user.repository.UserRepository;
import com.subject77.docshare.modules.organization.entities.MembershipStatus;
import com.subject77.docshare.modules.organization.entities.OrganizationEntity;
import com.subject77.docshare.modules.user.entities.UserOrganizationEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private com.subject77.docshare.modules.user.repository.UserOrganizationRepository userOrganizationRepository;

    @Mock
    private com.subject77.docshare.modules.policy.services.WorkspacePolicyService workspacePolicyService;

    @Mock
    private com.subject77.docshare.modules.trust.services.OrgTrustRelationshipService orgTrustRelationshipService;

    @Mock
    private com.subject77.docshare.modules.folder.repository.FolderRepository folderRepository;

    @Mock
    private com.subject77.docshare.modules.workspace.services.ProjectWorkspaceService projectWorkspaceService;

    @InjectMocks
    private UserServiceImpl userService;

    private static final String KEYCLOAK_ID = "7128fe1a-90d5-43ea-b3c7-1ffe727e8a68";
    private UserEntity existingUser;

    @BeforeEach
    void setUp() {
        existingUser = UserEntity.builder()
                .id(KEYCLOAK_ID)
                .email("miko@gmail.com")
                .firstName("miko")
                .lastName("miko")
                .role(Role.ROLE_USER)
                .build();
    }

    @Test
    @DisplayName("Should return existing user when already present in database")
    void getOrCreateCurrentUser_ShouldReturnExistingUser() {
        when(userRepository.findById(KEYCLOAK_ID)).thenReturn(Optional.of(existingUser));

        Jwt jwt = createJwt(Map.of("email", "miko@gmail.com", "given_name", "miko", "family_name", "miko"));

        UserEntity result = userService.getOrCreateCurrentUser(jwt);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(KEYCLOAK_ID);
        assertThat(result.getEmail()).isEqualTo("miko@gmail.com");

        verify(userRepository, times(1)).findById(KEYCLOAK_ID);
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should provision new user via JIT when user is not found in database")
    void getOrCreateCurrentUser_ShouldProvisionNewUser_WhenUserNotExists() {
        when(userRepository.findById(KEYCLOAK_ID)).thenReturn(Optional.empty());
        when(userRepository.save(any(UserEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Jwt jwt = createJwt(Map.of(
                "email", "miko@gmail.com",
                "given_name", "miko",
                "family_name", "miko"
        ));

        UserEntity result = userService.getOrCreateCurrentUser(jwt);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(KEYCLOAK_ID);
        assertThat(result.getEmail()).isEqualTo("miko@gmail.com");
        assertThat(result.getFirstName()).isEqualTo("miko");
        assertThat(result.getLastName()).isEqualTo("miko");
        assertThat(result.getRole()).isEqualTo(Role.ROLE_USER);

        ArgumentCaptor<UserEntity> userCaptor = ArgumentCaptor.forClass(UserEntity.class);
        verify(userRepository).save(userCaptor.capture());
        UserEntity savedUser = userCaptor.getValue();
        assertThat(savedUser.getId()).isEqualTo(KEYCLOAK_ID);
        assertThat(savedUser.getEmail()).isEqualTo("miko@gmail.com");
    }

    @Test
    @DisplayName("Should fallback to preferred_username or local email when email claim is missing")
    void getOrCreateCurrentUser_ShouldFallbackEmail_WhenEmailClaimMissing() {
        when(userRepository.findById(KEYCLOAK_ID)).thenReturn(Optional.empty());
        when(userRepository.save(any(UserEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Jwt jwt = createJwt(Map.of("preferred_username", "miko_user"));

        UserEntity result = userService.getOrCreateCurrentUser(jwt);

        assertThat(result.getEmail()).isEqualTo("miko_user");
    }

    @Test
    @DisplayName("Should map UserEntity to UserResponseDto in getCurrentUserProfile")
    void getCurrentUserProfile_ShouldReturnResponseDto() {
        when(userOrganizationRepository.findByUser_IdAndStatus(KEYCLOAK_ID, MembershipStatus.ACTIVE))
                .thenReturn(java.util.Collections.emptyList());

        UserResponseDto dto = userService.getCurrentUserProfile(existingUser);

        assertThat(dto).isNotNull();
        assertThat(dto.getId()).isEqualTo(KEYCLOAK_ID);
        assertThat(dto.getEmail()).isEqualTo("miko@gmail.com");
        assertThat(dto.getFirstName()).isEqualTo("miko");
        assertThat(dto.getLastName()).isEqualTo("miko");
        assertThat(dto.getRole()).isEqualTo(Role.ROLE_USER);
        assertThat(dto.getActiveOrganizations()).isEmpty();
    }

    @Test
    @DisplayName("Should update user profile fields and save entity")
    void updateUserProfile_ShouldUpdateFieldsAndSave() {
        when(userRepository.save(any(UserEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userOrganizationRepository.findByUser_IdAndStatus(KEYCLOAK_ID, MembershipStatus.ACTIVE))
                .thenReturn(java.util.Collections.emptyList());

        UserRequestDto requestDto = UserRequestDto.builder()
                .firstName("UpdatedFirstName")
                .lastName("UpdatedLastName")
                .build();

        UserResponseDto response = userService.updateUserProfile(existingUser, requestDto);

        assertThat(response).isNotNull();
        assertThat(response.getFirstName()).isEqualTo("UpdatedFirstName");
        assertThat(response.getLastName()).isEqualTo("UpdatedLastName");

        verify(userRepository).save(existingUser);
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException when user is not found by ID")
    void getUserById_ShouldThrowException_WhenUserNotFound() {
        when(userRepository.findById("non-existent-id")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getUserById("non-existent-id"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("User not found with id: non-existent-id");
    }

    @Test
    @DisplayName("Should return matching users with active organization memberships when valid search query is provided")
    void searchUsers_ShouldReturnMatchingUserResponseDtos_WhenValidQueryProvided() {
        OrganizationEntity org = OrganizationEntity.builder()
                .orgCode("ORG_A")
                .name("Alpha Corp")
                .build();

        UserOrganizationEntity activeMembership = UserOrganizationEntity.builder()
                .user(existingUser)
                .organization(org)
                .role(Role.ROLE_USER)
                .status(MembershipStatus.ACTIVE)
                .build();

        when(userRepository.searchUsers(eq("miko"), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(java.util.List.of(existingUser));
        when(userOrganizationRepository.findByUser_IdAndStatus(existingUser.getId(), MembershipStatus.ACTIVE))
                .thenReturn(java.util.List.of(activeMembership));

        java.util.List<UserResponseDto> results = userService.searchUsers("miko", existingUser);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getId()).isEqualTo(KEYCLOAK_ID);
        assertThat(results.get(0).getEmail()).isEqualTo("miko@gmail.com");
        assertThat(results.get(0).getActiveOrganizations()).hasSize(1);
        assertThat(results.get(0).getActiveOrganizations().get(0).getOrgCode()).isEqualTo("ORG_A");
        assertThat(results.get(0).getActiveOrganizations().get(0).getOrgName()).isEqualTo("Alpha Corp");
        assertThat(results.get(0).getActiveOrganizations().get(0).getStatus()).isEqualTo(MembershipStatus.ACTIVE);
        verify(userRepository).searchUsers(eq("miko"), any(org.springframework.data.domain.Pageable.class));
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when search query is null or blank")
    void searchUsers_ShouldThrowIllegalArgumentException_WhenQueryIsBlankOrNull() {
        assertThatThrownBy(() -> userService.searchUsers(null, existingUser))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Search query must not be blank");

        assertThatThrownBy(() -> userService.searchUsers("   ", existingUser))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Search query must not be blank");
    }

    @Test
    @DisplayName("Should throw AccessDeniedException when currentUser is null")
    void searchUsers_ShouldThrowAccessDeniedException_WhenCurrentUserIsNull() {
        assertThatThrownBy(() -> userService.searchUsers("miko", null))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class)
                .hasMessage("Authentication required");
    }


    private Jwt createJwt(Map<String, Object> claims) {
        Map<String, Object> headers = Map.of("alg", "RS256");
        Map<String, Object> fullClaims = new HashMap<>(claims);
        fullClaims.put("sub", KEYCLOAK_ID);

        return new Jwt(
                "token-string",
                Instant.now(),
                Instant.now().plusSeconds(3600),
                headers,
                fullClaims
        );
    }
}
