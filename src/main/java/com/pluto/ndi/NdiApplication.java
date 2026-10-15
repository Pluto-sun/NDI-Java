package com.pluto.ndi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class NdiApplication {

    public static void main(String[] args) {
        SpringApplication.run(NdiApplication.class, args);
    }
}
