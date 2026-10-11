package com.bjarne.datingrecommendationsuserservice.presence;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Every authenticated request counts as activity for the online status. Runs after the account status check.
 */
public class PresenceFilter extends OncePerRequestFilter {

    private final PresenceService presenceService;

    public PresenceFilter(PresenceService presenceService) {
        this.presenceService = presenceService;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwt) {
            presenceService.touch(jwt.getName());
        }
        chain.doFilter(request, response);
    }
}
