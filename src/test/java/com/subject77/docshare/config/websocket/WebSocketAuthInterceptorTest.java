package com.subject77.docshare.config.websocket;

import com.subject77.docshare.modules.channel.repository.ChannelMemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WebSocketAuthInterceptorTest {

    @Mock
    private JwtDecoder jwtDecoder;

    @Mock
    private JwtAuthenticationConverter jwtAuthenticationConverter;

    @Mock
    private ChannelMemberRepository channelMemberRepository;

    @Mock
    private MessageChannel messageChannel;

    @InjectMocks
    private WebSocketAuthInterceptor interceptor;

    private Jwt jwt;
    private JwtAuthenticationToken authentication;

    @BeforeEach
    void setUp() {
        jwt = Jwt.withTokenValue("valid.jwt.token")
                .header("alg", "RS256")
                .claim("sub", "usr-123")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();

        authentication = new JwtAuthenticationToken(jwt, Collections.emptyList(), "usr-123");
    }

    @Test
    @DisplayName("preSend on CONNECT with valid Bearer token should set authenticated user")
    void preSend_Connect_ValidBearerToken_ShouldSetUser() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setLeaveMutable(true);
        accessor.setNativeHeader("Authorization", "Bearer valid.jwt.token");
        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        when(jwtDecoder.decode("valid.jwt.token")).thenReturn(jwt);
        when(jwtAuthenticationConverter.convert(jwt)).thenReturn(authentication);

        Message<?> result = interceptor.preSend(message, messageChannel);

        assertThat(result).isNotNull();
        StompHeaderAccessor resultAccessor = StompHeaderAccessor.wrap(result);
        assertThat(resultAccessor.getUser()).isEqualTo(authentication);
        assertThat(resultAccessor.getUser().getName()).isEqualTo("usr-123");
    }

    @Test
    @DisplayName("preSend on CONNECT without token should throw BadCredentialsException")
    void preSend_Connect_MissingToken_ShouldThrowException() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThatThrownBy(() -> interceptor.preSend(message, messageChannel))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("Missing authentication token");
    }

    @Test
    @DisplayName("preSend on CONNECT with invalid JWT should throw BadCredentialsException")
    void preSend_Connect_InvalidJwt_ShouldThrowException() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setNativeHeader("Authorization", "Bearer invalid.jwt.token");
        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        when(jwtDecoder.decode("invalid.jwt.token")).thenThrow(new JwtException("Token expired"));

        assertThatThrownBy(() -> interceptor.preSend(message, messageChannel))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("Invalid JWT token");
    }

    @Test
    @DisplayName("preSend on SUBSCRIBE with unauthenticated user should throw AccessDeniedException")
    void preSend_Subscribe_Unauthenticated_ShouldThrowAccessDenied() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/user/usr-123/queue/notifications");
        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThatThrownBy(() -> interceptor.preSend(message, messageChannel))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Authentication required");
    }

    @Test
    @DisplayName("preSend on SUBSCRIBE to own user queue should succeed")
    void preSend_Subscribe_OwnQueue_ShouldSucceed() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setUser(authentication);
        accessor.setDestination("/user/usr-123/queue/notifications");
        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        Message<?> result = interceptor.preSend(message, messageChannel);

        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("preSend on SUBSCRIBE to another user's queue should throw AccessDeniedException")
    void preSend_Subscribe_OtherUserQueue_ShouldThrowAccessDenied() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setUser(authentication);
        accessor.setDestination("/user/usr-other-456/queue/notifications");
        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThatThrownBy(() -> interceptor.preSend(message, messageChannel))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Forbidden: You cannot subscribe to another user's destination");
    }

    @Test
    @DisplayName("preSend on SUBSCRIBE to channel topic as member should succeed")
    void preSend_Subscribe_ChannelTopic_Member_ShouldSucceed() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setUser(authentication);
        accessor.setDestination("/topic/channels/42");
        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        when(channelMemberRepository.existsByChannel_IdAndUser_Id(42L, "usr-123")).thenReturn(true);

        Message<?> result = interceptor.preSend(message, messageChannel);

        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("preSend on SUBSCRIBE to channel topic as non-member should throw AccessDeniedException")
    void preSend_Subscribe_ChannelTopic_NonMember_ShouldThrowAccessDenied() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setUser(authentication);
        accessor.setDestination("/topic/channels/42");
        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        when(channelMemberRepository.existsByChannel_IdAndUser_Id(42L, "usr-123")).thenReturn(false);

        assertThatThrownBy(() -> interceptor.preSend(message, messageChannel))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Forbidden: You must be a member of the channel to subscribe");
    }

    @Test
    @DisplayName("preSend on SUBSCRIBE to channel read receipts topic as member should succeed")
    void preSend_Subscribe_ChannelReadTopic_Member_ShouldSucceed() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setUser(authentication);
        accessor.setDestination("/topic/channels/42/read");
        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        when(channelMemberRepository.existsByChannel_IdAndUser_Id(42L, "usr-123")).thenReturn(true);

        Message<?> result = interceptor.preSend(message, messageChannel);

        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("preSend on SUBSCRIBE to channel read receipts topic as non-member should throw AccessDeniedException")
    void preSend_Subscribe_ChannelReadTopic_NonMember_ShouldThrowAccessDenied() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setUser(authentication);
        accessor.setDestination("/topic/channels/42/read");
        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        when(channelMemberRepository.existsByChannel_IdAndUser_Id(42L, "usr-123")).thenReturn(false);

        assertThatThrownBy(() -> interceptor.preSend(message, messageChannel))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Forbidden: You must be a member of the channel to subscribe");
    }
}
