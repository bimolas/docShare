package com.subject77.docshare.modules.user.services;

import com.subject77.docshare.modules.user.dto.UserRequestDto;
import com.subject77.docshare.modules.user.dto.UserResponseDto;
import com.subject77.docshare.modules.user.entities.UserEntity;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;

public interface UserService {
    /**
     * Resolves the local UserEntity from Postgres for the given Jwt.
     * If the user does not exist in Postgres yet (first login), performs
     * Just-In-Time (JIT) provisioning.
     */
    UserEntity getOrCreateCurrentUser(Jwt jwt);

    /**
     * Converts a UserEntity to a UserResponseDto.
     */
    UserResponseDto getCurrentUserProfile(UserEntity user);

    /**
     * Updates profile details for the active user.
     */
    UserResponseDto updateUserProfile(UserEntity user, UserRequestDto requestDto);

    /**
     * Retrieves a user by their Keycloak UUID.
     */
    UserEntity getUserById(String id);

    /**
     * Searches for users by email, first name, or last name.
     * Respects user and workspace discoverability policies relative to currentUser.
     */
    List<UserResponseDto> searchUsers(String query, UserEntity currentUser);

    /**
     * Searches for users with no currentUser context.
     */
    default List<UserResponseDto> searchUsers(String query) {
        return searchUsers(query, null);
    }
}
