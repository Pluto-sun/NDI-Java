package com.pluto.ndi.shared.security;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

class SessionCookieSupportTest {
    @Test void usesSecureHttpOnlySameSiteCookiesAndClearsTheSamePath() {
        var properties = new AuthenticationProperties(AuthenticationProperties.Mode.SESSION, 8, true);
        var issued = SessionCookieSupport.issue("test-value", properties);
        assertThat(issued.isSecure()).isTrue();
        assertThat(issued.isHttpOnly()).isTrue();
        assertThat(issued.getSameSite()).isEqualTo("Lax");
        assertThat(issued.getPath()).isEqualTo("/");
        assertThat(issued.getMaxAge()).hasSeconds(28800);
        var cleared = SessionCookieSupport.clear(properties);
        assertThat(cleared.getName()).isEqualTo(issued.getName());
        assertThat(cleared.getPath()).isEqualTo(issued.getPath());
        assertThat(cleared.getMaxAge()).isZero();
    }
}
