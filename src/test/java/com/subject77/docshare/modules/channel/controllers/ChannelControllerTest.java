package com.subject77.docshare.modules.channel.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.subject77.docshare.modules.channel.dto.*;
import com.subject77.docshare.modules.channel.entities.ChannelMemberRole;
import com.subject77.docshare.modules.channel.entities.ChannelStatus;
import com.subject77.docshare.modules.channel.entities.ChannelType;
import com.subject77.docshare.modules.channel.services.ChannelMessageService;
import com.subject77.docshare.modules.channel.services.ChannelService;
import com.subject77.docshare.modules.user.entities.Role;
import com.subject77.docshare.modules.user.entities.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class ChannelControllerTest {

    @Mock
    private ChannelService channelService;

    @Mock
    private ChannelMessageService channelMessageService;

    @InjectMocks
    private ChannelController channelController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;
    private UserEntity testUser;

    @BeforeEach
    void setUp() {
        testUser = UserEntity.builder()
                .id("user-101")
                .email("user@example.com")
                .firstName("Test")
                .lastName("User")
                .role(Role.ROLE_USER)
                .build();

        objectMapper = new ObjectMapper();

        mockMvc = MockMvcBuilders.standaloneSetup(channelController)
                .setCustomArgumentResolvers(new org.springframework.web.method.support.HandlerMethodArgumentResolver() {
                    @Override
                    public boolean supportsParameter(org.springframework.core.MethodParameter parameter) {
                        return parameter.getParameterType().equals(UserEntity.class);
                    }

                    @Override
                    public Object resolveArgument(org.springframework.core.MethodParameter parameter,
                                                  org.springframework.web.method.support.ModelAndViewContainer mavContainer,
                                                  org.springframework.web.context.request.NativeWebRequest webRequest,
                                                  org.springframework.web.bind.support.WebDataBinderFactory binderFactory) {
                        return testUser;
                    }
                })
                .build();
    }

    @Test
    @DisplayName("POST /api/v1/channels should create channel and return 201 CREATED")
    void createChannel_success() throws Exception {
        CreateChannelRequestDto request = CreateChannelRequestDto.builder()
                .name("general")
                .description("General discussion")
                .type(ChannelType.STANDARD)
                .orgCode("ORG-A")
                .build();

        ChannelResponseDto response = ChannelResponseDto.builder()
                .id(1L)
                .name("general")
                .description("General discussion")
                .type(ChannelType.STANDARD)
                .status(ChannelStatus.ACTIVE)
                .orgCode("ORG-A")
                .createdById("user-101")
                .createdByEmail("user@example.com")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .memberCount(1L)
                .unreadCount(0L)
                .build();

        when(channelService.createChannel(any(CreateChannelRequestDto.class), any(UserEntity.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/v1/channels")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("general"))
                .andExpect(jsonPath("$.type").value("STANDARD"));
    }

    @Test
    @DisplayName("GET /api/v1/channels/{channelId} should return channel details")
    void getChannel_success() throws Exception {
        ChannelResponseDto response = ChannelResponseDto.builder()
                .id(1L)
                .name("general")
                .type(ChannelType.STANDARD)
                .status(ChannelStatus.ACTIVE)
                .build();

        when(channelService.getChannel(eq(1L), any(UserEntity.class)))
                .thenReturn(response);

        mockMvc.perform(get("/api/v1/channels/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("general"));
    }

    @Test
    @DisplayName("GET /api/v1/channels should list channels")
    void listChannels_success() throws Exception {
        ChannelResponseDto response = ChannelResponseDto.builder()
                .id(1L)
                .name("general")
                .type(ChannelType.STANDARD)
                .build();

        when(channelService.listChannels(eq("ORG-A"), any(), any(), any(), any(UserEntity.class)))
                .thenReturn(List.of(response));

        mockMvc.perform(get("/api/v1/channels").param("orgCode", "ORG-A"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].name").value("general"));
    }

    @Test
    @DisplayName("PUT /api/v1/channels/{channelId} should update channel settings")
    void updateChannel_success() throws Exception {
        UpdateChannelRequestDto request = UpdateChannelRequestDto.builder()
                .name("updated-name")
                .description("Updated description")
                .build();

        ChannelResponseDto response = ChannelResponseDto.builder()
                .id(1L)
                .name("updated-name")
                .description("Updated description")
                .type(ChannelType.STANDARD)
                .status(ChannelStatus.ACTIVE)
                .build();

        when(channelService.updateChannel(eq(1L), any(UpdateChannelRequestDto.class), any(UserEntity.class)))
                .thenReturn(response);

        mockMvc.perform(put("/api/v1/channels/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("updated-name"))
                .andExpect(jsonPath("$.description").value("Updated description"));
    }

    @Test
    @DisplayName("POST /api/v1/channels/{channelId}/archive should archive channel")
    void archiveChannel_success() throws Exception {
        ChannelResponseDto response = ChannelResponseDto.builder()
                .id(1L)
                .name("general")
                .status(ChannelStatus.ARCHIVED)
                .build();

        when(channelService.archiveChannel(eq(1L), any(UserEntity.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/v1/channels/1/archive"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ARCHIVED"));
    }

    @Test
    @DisplayName("POST /api/v1/channels/{channelId}/members should add member")
    void addMember_success() throws Exception {
        AddChannelMemberRequestDto request = AddChannelMemberRequestDto.builder()
                .userEmail("member@example.com")
                .role(ChannelMemberRole.MEMBER)
                .build();

        ChannelMemberResponseDto response = ChannelMemberResponseDto.builder()
                .id(10L)
                .channelId(1L)
                .userId("user-202")
                .userEmail("member@example.com")
                .role(ChannelMemberRole.MEMBER)
                .build();

        when(channelService.addMember(eq(1L), any(AddChannelMemberRequestDto.class), any(UserEntity.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/v1/channels/1/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value("user-202"))
                .andExpect(jsonPath("$.role").value("MEMBER"));
    }

    @Test
    @DisplayName("GET /api/v1/channels/{channelId}/members should list channel members")
    void listMembers_success() throws Exception {
        ChannelMemberResponseDto response = ChannelMemberResponseDto.builder()
                .id(10L)
                .channelId(1L)
                .userId("user-101")
                .userEmail("user@example.com")
                .role(ChannelMemberRole.MANAGER)
                .build();

        when(channelService.listMembers(eq(1L), any(UserEntity.class)))
                .thenReturn(List.of(response));

        mockMvc.perform(get("/api/v1/channels/1/members"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].userId").value("user-101"))
                .andExpect(jsonPath("$[0].role").value("MANAGER"));
    }

    @Test
    @DisplayName("PUT /api/v1/channels/{channelId}/members/{userId}/role should update member role")
    void updateMemberRole_success() throws Exception {
        UpdateChannelMemberRoleRequestDto request = UpdateChannelMemberRoleRequestDto.builder()
                .role(ChannelMemberRole.MANAGER)
                .build();

        ChannelMemberResponseDto response = ChannelMemberResponseDto.builder()
                .id(10L)
                .channelId(1L)
                .userId("user-202")
                .role(ChannelMemberRole.MANAGER)
                .build();

        when(channelService.updateMemberRole(eq(1L), eq("user-202"), any(UpdateChannelMemberRoleRequestDto.class), any(UserEntity.class)))
                .thenReturn(response);

        mockMvc.perform(put("/api/v1/channels/1/members/user-202/role")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("user-202"))
                .andExpect(jsonPath("$.role").value("MANAGER"));
    }

    @Test
    @DisplayName("DELETE /api/v1/channels/{channelId}/members/{userId} should remove member and return 244 NO CONTENT")
    void removeMember_success() throws Exception {
        doNothing().when(channelService).removeMember(eq(1L), eq("user-202"), any(UserEntity.class));

        mockMvc.perform(delete("/api/v1/channels/1/members/user-202"))
                .andExpect(status().isNoContent());

        verify(channelService, times(1)).removeMember(eq(1L), eq("user-202"), any(UserEntity.class));
    }

    @Test
    @DisplayName("POST /api/v1/channels/{channelId}/read should mark channel as read")
    void markChannelAsRead_success() throws Exception {
        doNothing().when(channelMessageService).markChannelAsRead(eq(1L), any(UserEntity.class));

        mockMvc.perform(post("/api/v1/channels/1/read"))
                .andExpect(status().isOk());

        verify(channelMessageService, times(1)).markChannelAsRead(eq(1L), any(UserEntity.class));
    }
}
