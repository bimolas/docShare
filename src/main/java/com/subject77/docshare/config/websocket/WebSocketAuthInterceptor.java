package com.subject77.docshare.config.websocket;

import com.subject77.docshare.modules.channel.repository.ChannelMemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    private final JwtDecoder jwtDecoder;
    private final JwtAuthenticationConverter jwtAuthenticationConverter;
    private final ChannelMemberRepository channelMemberRepository;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) {
            accessor = StompHeaderAccessor.wrap(message);
        }

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            String token = extractToken(accessor);
            if (!StringUtils.hasText(token)) {
                log.warn("STOMP CONNECT rejected: No authentication token provided");
                throw new BadCredentialsException("Missing authentication token in STOMP CONNECT frame");
            }

            try {
                Jwt jwt = jwtDecoder.decode(token);
                Authentication authentication = jwtAuthenticationConverter.convert(jwt);
                accessor.setUser(authentication);
                log.info("STOMP CONNECT authenticated successfully for user: {}", authentication.getName());
            } catch (JwtException e) {
                log.warn("STOMP CONNECT rejected: Invalid JWT token - {}", e.getMessage());
                throw new BadCredentialsException("Invalid JWT token: " + e.getMessage());
            }
        } else if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            Authentication auth = (Authentication) accessor.getUser();
            if (auth == null || !auth.isAuthenticated()) {
                log.warn("STOMP SUBSCRIBE rejected: Unauthenticated user");
                throw new AccessDeniedException("Authentication required to subscribe");
            }

            String destination = accessor.getDestination();
            if (destination != null) {
                // Enforce authorization for explicit user destination paths
                // E.g., if subscribing to /user/{userId}/... verify userId matches authenticated user
                String currentUserId = auth.getName();
                if (destination.startsWith("/user/")) {
                    String subPath = destination.substring(6); // strip "/user/"
                    int nextSlash = subPath.indexOf('/');
                    if (nextSlash > 0) {
                        String targetUserId = subPath.substring(0, nextSlash);
                        // If segment matches known system destinations (like "queue" or "topic"), allow.
                        // Otherwise, if an explicit target user ID is supplied, verify it matches currentUserId.
                        if (!"queue".equals(targetUserId) && !"topic".equals(targetUserId) && !targetUserId.equals(currentUserId)) {
                            log.warn("STOMP SUBSCRIBE forbidden: User {} attempted to subscribe to user {} destination: {}",
                                    currentUserId, targetUserId, destination);
                            throw new AccessDeniedException("Forbidden: You cannot subscribe to another user's destination");
                        }
                    }
                } else if (destination.startsWith("/topic/channels/")) {
                    String subPath = destination.substring("/topic/channels/".length());
                    int nextSlash = subPath.indexOf('/');
                    String channelIdStr = (nextSlash > 0) ? subPath.substring(0, nextSlash) : subPath;
                    try {
                        Long channelId = Long.parseLong(channelIdStr);
                        if (channelMemberRepository != null && !channelMemberRepository.existsByChannel_IdAndUser_Id(channelId, currentUserId)) {
                            log.warn("STOMP SUBSCRIBE forbidden: User {} is not a member of channel {}", currentUserId, channelId);
                            throw new AccessDeniedException("Forbidden: You must be a member of the channel to subscribe");
                        }
                    } catch (NumberFormatException e) {
                        log.warn("Invalid channel ID format in STOMP SUBSCRIBE destination: {}", destination);
                    }
                }
            }
        }

        return message;
    }

    private String extractToken(StompHeaderAccessor accessor) {
        // 1. Check "Authorization" header in STOMP native headers
        List<String> authHeaders = accessor.getNativeHeader("Authorization");
        if (authHeaders != null && !authHeaders.isEmpty()) {
            String bearerToken = authHeaders.get(0);
            if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
                return bearerToken.substring(7);
            }
        }

        // 2. Check "token" or "access_token" header
        List<String> tokenHeaders = accessor.getNativeHeader("token");
        if (tokenHeaders != null && !tokenHeaders.isEmpty()) {
            return tokenHeaders.get(0);
        }

        List<String> accessTokenHeaders = accessor.getNativeHeader("access_token");
        if (accessTokenHeaders != null && !accessTokenHeaders.isEmpty()) {
            return accessTokenHeaders.get(0);
        }

        return null;
    }
}
