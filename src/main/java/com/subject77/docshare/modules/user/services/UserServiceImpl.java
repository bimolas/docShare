package com.subject77.docshare.modules.user.services;

import com.subject77.docshare.exceptions.ResourceNotFoundException;
import com.subject77.docshare.modules.user.dto.UserRequestDto;
import com.subject77.docshare.modules.user.dto.UserResponseDto;
import com.subject77.docshare.modules.user.entities.Role;
import com.subject77.docshare.modules.user.entities.UserEntity;
import com.subject77.docshare.modules.user.mapper.UserMapper;
import com.subject77.docshare.modules.user.repository.UserRepository;
import com.subject77.docshare.modules.policy.entities.DiscoverabilityMode;
import com.subject77.docshare.modules.policy.entities.WorkspacePolicyEntity;
import com.subject77.docshare.modules.policy.services.WorkspacePolicyService;
import com.subject77.docshare.modules.trust.services.OrgTrustRelationshipService;
import com.subject77.docshare.modules.user.entities.UserOrganizationEntity;
import com.subject77.docshare.modules.user.repository.UserOrganizationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import com.subject77.docshare.modules.organization.entities.MembershipStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.subject77.docshare.modules.folder.entities.FolderEntity;
import com.subject77.docshare.modules.folder.entities.SubspaceType;
import com.subject77.docshare.modules.folder.repository.FolderRepository;
import com.subject77.docshare.modules.workspace.services.ProjectWorkspaceService;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final UserOrganizationRepository userOrganizationRepository;
    private final WorkspacePolicyService workspacePolicyService;
    private final OrgTrustRelationshipService orgTrustRelationshipService;
    private final FolderRepository folderRepository;
    private final ProjectWorkspaceService projectWorkspaceService;

    @Override
    @Transactional
    @Cacheable(value = "users", key = "#jwt.subject")
    public UserEntity getOrCreateCurrentUser(Jwt jwt) {
        String keycloakId = jwt.getSubject();

        UserEntity user = userRepository.findById(keycloakId)
                .orElseGet(() -> provisionNewUser(jwt, keycloakId));

        projectWorkspaceService.ensureDefaultPersonalWorkspace(user);
        return user;
    }

    private UserEntity provisionNewUser(Jwt jwt, String keycloakId) {
        String email = jwt.getClaimAsString("email");
        if (email == null || email.isBlank()) {
            email = jwt.getClaimAsString("preferred_username");
        }
        if (email == null || email.isBlank()) {
            email = keycloakId + "@docshare.local";
        }

        String firstName = jwt.getClaimAsString("given_name");
        String lastName = jwt.getClaimAsString("family_name");

        log.info("Performing Just-In-Time (JIT) provisioning for user ID: {} with email: {}", keycloakId, email);

        UserEntity newUser = UserEntity.builder()
                .id(keycloakId)
                .email(email)
                .firstName(firstName != null ? firstName : "User")
                .lastName(lastName != null ? lastName : "")
                .role(Role.ROLE_USER)
                .build();

        UserEntity savedUser = userRepository.save(newUser);

        // Provision default personal workspace and root folder idempotently
        projectWorkspaceService.ensureDefaultPersonalWorkspace(savedUser);

        return savedUser;
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponseDto getCurrentUserProfile(UserEntity user) {
        List<UserOrganizationEntity> activeMemberships = userOrganizationRepository.findByUser_IdAndStatus(user.getId(),
                MembershipStatus.ACTIVE);
        return UserMapper.toResponseDto(user, activeMemberships);
    }

    @Override
    @Transactional
    @CacheEvict(value = "users", key = "#user.id")
    public UserResponseDto updateUserProfile(UserEntity user, UserRequestDto requestDto) {
        if (requestDto.getFirstName() != null) {
            user.setFirstName(requestDto.getFirstName());
        }
        if (requestDto.getLastName() != null) {
            user.setLastName(requestDto.getLastName());
        }

        UserEntity updatedUser = userRepository.save(user);
        List<UserOrganizationEntity> activeMemberships = userOrganizationRepository.findByUser_IdAndStatus(user.getId(),
                MembershipStatus.ACTIVE);
        return UserMapper.toResponseDto(updatedUser, activeMemberships);
    }

    @Override
    @Transactional(readOnly = true)
    public UserEntity getUserById(String id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserResponseDto> searchUsers(String query, UserEntity currentUser) {
        if (query == null || query.trim().isBlank()) {
            throw new IllegalArgumentException("Search query must not be blank");
        }

        if (currentUser == null) {
            throw new AccessDeniedException("Authentication required");
        }

        Pageable pageable = PageRequest.of(0, 50);
        List<UserEntity> candidates = userRepository.searchUsers(query.trim(), pageable);

        Set<String> requesterOrgCodes = userOrganizationRepository
                .findByUser_IdAndStatus(currentUser.getId(), MembershipStatus.ACTIVE).stream()
                .map(m -> m.getOrganization().getOrgCode())
                .collect(Collectors.toSet());

        List<UserResponseDto> results = new ArrayList<>();
        for (UserEntity targetUser : candidates) {
            List<UserOrganizationEntity> activeMemberships = userOrganizationRepository
                    .findByUser_IdAndStatus(targetUser.getId(), MembershipStatus.ACTIVE);
            if (isUserDiscoverableToUser(targetUser, currentUser, requesterOrgCodes, activeMemberships)) {
                results.add(UserMapper.toResponseDto(targetUser, activeMemberships));
                if (results.size() >= 10) {
                    break;
                }
            }
        }
        return results;
    }

    private boolean isUserDiscoverableToUser(UserEntity targetUser, UserEntity currentUser,
            Set<String> requesterOrgCodes, List<UserOrganizationEntity> targetActiveMemberships) {
        if (targetUser.getId().equals(currentUser.getId())) {
            return true;
        }

        Set<String> targetOrgCodes = targetActiveMemberships.stream()
                .map(m -> m.getOrganization().getOrgCode())
                .collect(Collectors.toSet());

        // Same-organization check
        boolean isSameOrg = targetOrgCodes.stream().anyMatch(requesterOrgCodes::contains);
        if (isSameOrg) {
            return true;
        }

        // Check target's organization discoverability policies
        for (String targetOrgCode : targetOrgCodes) {
            DiscoverabilityMode mode = workspacePolicyService.getPolicyForOrganization(targetOrgCode)
                    .map(WorkspacePolicyEntity::getDiscoverabilityMode)
                    .orElse(DiscoverabilityMode.DISCOVERABLE);

            if (mode == DiscoverabilityMode.DISCOVERABLE) {
                return true;
            } else if (mode == DiscoverabilityMode.TRUSTED_ONLY) {
                if (!requesterOrgCodes.isEmpty()) {
                    boolean hasTrust = requesterOrgCodes.stream()
                            .anyMatch(reqOrgCode -> orgTrustRelationshipService.hasActiveTrust(reqOrgCode,
                                    targetOrgCode));
                    if (hasTrust) {
                        return true;
                    }
                }
            }
        }

        // Check target user's personal policy
        DiscoverabilityMode personalMode = workspacePolicyService.getPolicyForUser(targetUser.getId())
                .map(WorkspacePolicyEntity::getDiscoverabilityMode)
                .orElse(DiscoverabilityMode.PRIVATE);

        if (personalMode == DiscoverabilityMode.DISCOVERABLE) {
            return true;
        }

        return false;
    }
}
