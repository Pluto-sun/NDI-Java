package com.pluto.ndi.shared.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseCookie;

/** 会话标识只通过同站 Cookie 传递，不写入接口响应正文。 */
public final class SessionCookieSupport {
    public static final String COOKIE_NAME = "ndi_session";
    private SessionCookieSupport() { }

    public static String read(HttpServletRequest request) {
        if (request.getCookies() == null) return null;
        for (var cookie : request.getCookies()) {
            if (COOKIE_NAME.equals(cookie.getName())) return cookie.getValue();
        }
        return null;
    }

    public static ResponseCookie issue(String value, AuthenticationProperties properties) {
        return cookie(value, properties, properties.sessionTtlHours() * 3600L);
    }

    public static ResponseCookie clear(AuthenticationProperties properties) { return cookie("", properties, 0); }

    private static ResponseCookie cookie(String value, AuthenticationProperties properties, long seconds) {
        return ResponseCookie.from(COOKIE_NAME, value).httpOnly(true).secure(properties.secureCookie())
                .sameSite("Lax").path("/").maxAge(seconds).build();
    }
}
