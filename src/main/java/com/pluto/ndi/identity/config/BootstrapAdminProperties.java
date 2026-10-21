package com.pluto.ndi.identity.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("ndi.bootstrap")
public record BootstrapAdminProperties(@DefaultValue("") String adminLogin, @DefaultValue("") String adminPassword) {
    @Override public String toString() { return "BootstrapAdminProperties[凭据已隐藏]"; }
}
