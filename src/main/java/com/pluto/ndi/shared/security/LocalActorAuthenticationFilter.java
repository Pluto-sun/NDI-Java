package com.pluto.ndi.shared.security;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.filter.OncePerRequestFilter;

/** 仅供 local 环境以账号标识联调接口，生产环境不得启用。 */
@Component
@Profile("local")
@ConditionalOnProperty(name = "ndi.auth.mode", havingValue = "LOCAL_ACTOR")
public class LocalActorAuthenticationFilter extends OncePerRequestFilter {

    static final String ACTOR_ID_HEADER = "X-NDI-Actor-Id";

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        var actorIdHeader = request.getHeader(ACTOR_ID_HEADER);
        if (actorIdHeader != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            parseUserId(actorIdHeader).ifPresent(userId -> SecurityContextHolder.getContext().setAuthentication(
                    UsernamePasswordAuthenticationToken.authenticated(new ActorPrincipal(userId), null, List.of())
            ));
        }
        filterChain.doFilter(request, response);
    }

    private java.util.Optional<UUID> parseUserId(String value) {
        try {
            return java.util.Optional.of(UUID.fromString(value));
        } catch (IllegalArgumentException exception) {
            return java.util.Optional.empty();
        }
    }
}
