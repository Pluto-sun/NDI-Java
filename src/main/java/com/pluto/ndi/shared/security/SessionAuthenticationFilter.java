package com.pluto.ndi.shared.security;

import java.io.IOException;
import java.util.List;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/** 每次请求重验会话和账号状态，密码变更或停用后立即失效。 */
final class SessionAuthenticationFilter extends OncePerRequestFilter {
    private final ObjectProvider<ActorSessionDirectory> sessions;

    SessionAuthenticationFilter(ObjectProvider<ActorSessionDirectory> sessions) { this.sessions = sessions; }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) { return !request.getRequestURI().startsWith("/api/"); }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var token = SessionCookieSupport.read(request);
        var directory = sessions.getIfAvailable();
        if (token != null && directory != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            directory.resolveActor(token).ifPresent(id -> SecurityContextHolder.getContext().setAuthentication(
                    UsernamePasswordAuthenticationToken.authenticated(new ActorPrincipal(id), null, List.of())));
        }
        chain.doFilter(request, response);
    }
}
