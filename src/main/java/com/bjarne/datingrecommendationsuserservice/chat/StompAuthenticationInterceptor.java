package com.bjarne.datingrecommendationsuserservice.chat;

import org.jspecify.annotations.NonNull;
import com.bjarne.datingrecommendationsuserservice.security.AccountStatusService;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.stereotype.Component;

/**
 * Authenticates STOMP sessions with the same JWT as the REST API. Browsers cannot send headers with the
 * WebSocket handshake, so the token is sent in the CONNECT frame: {@code Authorization: Bearer <token>}.
 * Clients may only subscribe to their own user destinations (/user/queue/...) and send to /app/...
 */
@Component
public class StompAuthenticationInterceptor implements ChannelInterceptor {

    private static final String BEARER = "Bearer ";

    private final JwtDecoder jwtDecoder;
    private final JwtAuthenticationConverter jwtAuthenticationConverter;
    private final AccountStatusService accountStatusService;

    public StompAuthenticationInterceptor(JwtDecoder jwtDecoder, JwtAuthenticationConverter jwtAuthenticationConverter,
                                          AccountStatusService accountStatusService) {
        this.accountStatusService = accountStatusService;
        this.jwtDecoder = jwtDecoder;
        this.jwtAuthenticationConverter = jwtAuthenticationConverter;
    }

    @Override
    public Message<?> preSend(@NonNull Message<?> message, @NonNull MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }

        StompCommand command = accessor.getCommand();
        if (command == StompCommand.CONNECT) {
            accessor.setUser(authenticate(accessor.getFirstNativeHeader("Authorization")));
        } else if (command == StompCommand.SUBSCRIBE) {
            requireUser(accessor);
            requireDestination(accessor, "/user/queue/");
        } else if (command == StompCommand.SEND) {
            requireUser(accessor);
            requireDestination(accessor, "/app/");
        }
        return message;
    }

    private org.springframework.security.core.Authentication authenticate(String header) {
        if (header == null || !header.startsWith(BEARER)) {
            throw new MessageDeliveryException("Missing bearer token in CONNECT frame");
        }
        try {
            var authentication = jwtAuthenticationConverter.convert(jwtDecoder.decode(header.substring(BEARER.length())));
            if (!accountStatusService.mayUseApi(authentication.getName())) {
                throw new MessageDeliveryException("Account is blocked or deleted");
            }
            return authentication;
        } catch (JwtException e) {
            throw new MessageDeliveryException("Invalid token: " + e.getMessage());
        }
    }

    private static void requireUser(StompHeaderAccessor accessor) {
        if (accessor.getUser() == null) {
            throw new MessageDeliveryException("Not authenticated");
        }
    }

    private static void requireDestination(StompHeaderAccessor accessor, String prefix) {
        String destination = accessor.getDestination();
        if (destination == null || !destination.startsWith(prefix)) {
            throw new MessageDeliveryException("Destination not allowed: " + destination);
        }
    }
}
