package com.pluto.ndi.shared.security;

import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pluto.ndi.shared.config.TimeConfiguration;
import com.pluto.ndi.shared.web.ProblemDetailFactory;
import com.pluto.ndi.shared.web.ProblemDetailResponseWriter;
import com.pluto.ndi.shared.web.RequestCorrelationFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(controllers = ApiSecurityContractTest.SecuredController.class)
@ImportAutoConfiguration(exclude = UserDetailsServiceAutoConfiguration.class)
@Import({
        SecurityConfig.class,
        RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class,
        ProblemDetailFactory.class,
        ProblemDetailResponseWriter.class,
        RequestCorrelationFilter.class,
        TimeConfiguration.class
})
class ApiSecurityContractTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void returnsProblemDetailForUnauthenticatedRequest() throws Exception {
        mockMvc.perform(get("/api/v1/secured").header("X-Request-Id", "unauthenticated-123"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(header().string("X-Request-Id", "unauthenticated-123"))
                .andExpect(jsonPath("$.code", is("AUTH_UNAUTHENTICATED")))
                .andExpect(jsonPath("$.requestId", is("unauthenticated-123")));
    }

    @Test
    void returnsProblemDetailForForbiddenRequest() throws Exception {
        mockMvc.perform(get("/api/v1/secured")
                        .with(user("test-user"))
                        .header("X-Request-Id", "forbidden-123"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(header().string("X-Request-Id", "forbidden-123"))
                .andExpect(jsonPath("$.code", is("AUTH_FORBIDDEN")))
                .andExpect(jsonPath("$.requestId", is("forbidden-123")));
    }

    @RestController
    static class SecuredController {

        @GetMapping("/api/v1/secured")
        String secured() {
            return "should not be reachable";
        }
    }
}
