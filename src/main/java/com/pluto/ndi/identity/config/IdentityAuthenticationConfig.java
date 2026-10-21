package com.pluto.ndi.identity.config;

import java.util.Map;
import org.springframework.context.annotation.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.security.crypto.password.*;

@Configuration
@EnableConfigurationProperties(BootstrapAdminProperties.class)
public class IdentityAuthenticationConfig {
    @Bean
    PasswordEncoder identityPasswordEncoder() {
        return new DelegatingPasswordEncoder("pbkdf2",
                Map.of("pbkdf2", Pbkdf2PasswordEncoder.defaultsForSpringSecurity_v5_8()));
    }
}
