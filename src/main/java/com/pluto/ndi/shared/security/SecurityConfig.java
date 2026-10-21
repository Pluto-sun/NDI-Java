package com.pluto.ndi.shared.security;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(AuthenticationProperties.class)
class SecurityConfig {

    @Bean
    CookieCsrfTokenRepository csrfTokenRepository(AuthenticationProperties properties) {
        var repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookieCustomizer(cookie -> cookie.secure(properties.secureCookie()).sameSite("Lax").path("/"));
        return repository;
    }

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            RestAuthenticationEntryPoint authenticationEntryPoint,
            RestAccessDeniedHandler accessDeniedHandler,
            ObjectProvider<LocalActorAuthenticationFilter> localActorAuthenticationFilter,
            ObjectProvider<ActorSessionDirectory> actorSessions,
            AuthenticationProperties properties, Environment environment,
            CookieCsrfTokenRepository csrfRepository
    ) throws Exception {
        boolean localActor = properties.mode() == AuthenticationProperties.Mode.LOCAL_ACTOR;
        if (localActor && !environment.acceptsProfiles(Profiles.of("local"))) {
            throw new IllegalStateException("开发身份模式仅允许在 local 环境启用");
        }
        http
                .sessionManagement(sessions -> sessions.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .exceptionHandling(exceptionHandling -> exceptionHandling
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        .requestMatchers("/api/v1/auth/mode", "/api/v1/auth/csrf", "/api/v1/auth/sessions").permitAll()
                        .requestMatchers("/api/v1/projects/dev/actors").access((authentication, context) ->
                                new org.springframework.security.authorization.AuthorizationDecision(localActor))
                        .requestMatchers("/api/v1/auth/**").authenticated()
                        .requestMatchers("/api/v1/admin/**").authenticated()
                        .requestMatchers("/api/v1/projects/**").authenticated()
                        .anyRequest().denyAll());
        if (localActor) {
            http.csrf(AbstractHttpConfigurer::disable);
            localActorAuthenticationFilter.ifAvailable(filter -> http.addFilterBefore(filter, AnonymousAuthenticationFilter.class));
        } else {
            http.csrf(csrf -> csrf.csrfTokenRepository(csrfRepository)
                    .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()));
            http.addFilterBefore(new SessionAuthenticationFilter(actorSessions), AnonymousAuthenticationFilter.class);
        }
        return http.build();
    }
}
