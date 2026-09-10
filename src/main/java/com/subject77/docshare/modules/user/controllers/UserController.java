package com.subject77.docshare.modules.user.controllers;

import com.subject77.docshare.config.security.CurrentUser;
import com.subject77.docshare.modules.user.dto.UserRequestDto;
import com.subject77.docshare.modules.user.dto.UserResponseDto;
import com.subject77.docshare.modules.user.entities.UserEntity;
import com.subject77.docshare.modules.user.services.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST Controller for managing current authenticated user profile and user
 * discovery.
 */
@PreAuthorize("isAuthenticated()")
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /**
     * Retrieves the profile of the currently authenticated user.
     */
    @GetMapping("/me")
    public ResponseEntity<UserResponseDto> getMyProfile(@CurrentUser UserEntity currentUser) {
        UserResponseDto profile = userService.getCurrentUserProfile(currentUser);
        return ResponseEntity.ok(profile);
    }

    /**
     * Updates profile details of the currently authenticated user.
     */
    @PutMapping("/me")
    public ResponseEntity<UserResponseDto> updateMyProfile(
            @CurrentUser UserEntity currentUser,
            @RequestBody UserRequestDto requestDto) {
        UserResponseDto updatedProfile = userService.updateUserProfile(currentUser, requestDto);
        return ResponseEntity.ok(updatedProfile);
    }

    /**
     * Searches for existing SecureDoc users by email, first name, or last name.
     */
    @GetMapping("/search")
    public ResponseEntity<List<UserResponseDto>> searchUsers(
            @RequestParam(name = "q", required = false) String query,
            @CurrentUser UserEntity currentUser) {
        List<UserResponseDto> results = userService.searchUsers(query, currentUser);
        return ResponseEntity.ok(results);
    }
}
