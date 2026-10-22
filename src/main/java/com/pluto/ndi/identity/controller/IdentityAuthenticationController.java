package com.pluto.ndi.identity.controller;

import java.util.Map;
import java.util.UUID;
import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import com.pluto.ndi.identity.domain.IdentityErrorCode;
import com.pluto.ndi.identity.service.*;
import com.pluto.ndi.shared.security.*;
import com.pluto.ndi.shared.web.*;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class IdentityAuthenticationController {
    private final IdentitySessionService sessions;
    private final IdentityProfileService profiles;
    private final AuthenticationProperties properties;
    private final CookieCsrfTokenRepository csrf;
    private final ApiResponseFactory responses;
    public IdentityAuthenticationController(IdentitySessionService sessions, IdentityProfileService profiles,
            AuthenticationProperties properties, CookieCsrfTokenRepository csrf, ApiResponseFactory responses) {
        this.sessions = sessions; this.profiles = profiles; this.properties = properties;
        this.csrf = csrf; this.responses = responses;
    }

    @GetMapping("/mode")
    public ApiResponse<?> mode(HttpServletResponse response) {
        noStore(response); return responses.success(Map.of("mode", properties.mode()));
    }
    @GetMapping("/csrf")
    public ApiResponse<?> csrf(HttpServletRequest request, HttpServletResponse response) {
        noStore(response); sessions.requireSessionMode();
        return responses.success(Map.of("token", ((CsrfToken) request.getAttribute(CsrfToken.class.getName())).getToken()));
    }
    @PostMapping("/sessions")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<IdentityUserView> login(@Valid @RequestBody LoginRequest body,
            HttpServletRequest request, HttpServletResponse response) {
        noStore(response);
        var result = sessions.login(body.loginName(), body.password());
        response.addHeader(HttpHeaders.SET_COOKIE, SessionCookieSupport.issue(result.token(), properties).toString());
        csrf.saveToken(null, request, response);
        return responses.success(result.user());
    }
    @GetMapping("/me")
    public ApiResponse<IdentityUserView> me(Authentication auth, HttpServletResponse response) {
        noStore(response); return responses.success(profiles.current(actorId(auth)));
    }
    @PutMapping("/me")
    public ApiResponse<IdentityUserView> profile(Authentication auth, @Valid @RequestBody ProfileRequest body,
            HttpServletResponse response) {
        noStore(response);
        return responses.success(profiles.update(actorId(auth), body.displayName(), body.email()));
    }
    @DeleteMapping("/sessions/current")
    public ApiResponse<?> logout(Authentication auth, HttpServletRequest request, HttpServletResponse response) {
        noStore(response); sessions.logout(actorId(auth), SessionCookieSupport.read(request));
        clear(request, response); return responses.success(null);
    }
    @PostMapping("/password")
    public ApiResponse<?> password(Authentication auth, @Valid @RequestBody PasswordRequest body,
            HttpServletRequest request, HttpServletResponse response) {
        noStore(response);
        profiles.changePassword(actorId(auth), body.currentPassword(), body.newPassword());
        clear(request, response); return responses.success(null);
    }
    private void clear(HttpServletRequest request, HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, SessionCookieSupport.clear(properties).toString());
        csrf.saveToken(null, request, response);
    }
    private void noStore(HttpServletResponse response) { response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store"); }
    private UUID actorId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof ActorPrincipal principal)) {
            throw new BusinessException(IdentityErrorCode.SESSION_INVALID);
        }
        return principal.userId();
    }
    public record LoginRequest(@NotBlank @Size(max = 80) String loginName, @NotBlank @Size(max = 128) String password) {
        @Override public String toString() { return "LoginRequest[凭据已隐藏]"; }
    }
    public record ProfileRequest(@NotBlank @Size(max = 100) String displayName, @Email @Size(max = 254) String email) { }
    public record PasswordRequest(@NotBlank @Size(max = 128) String currentPassword,
            @NotBlank @Size(min = 6, max = 128) String newPassword) {
        @Override public String toString() { return "PasswordRequest[凭据已隐藏]"; }
    }
}
