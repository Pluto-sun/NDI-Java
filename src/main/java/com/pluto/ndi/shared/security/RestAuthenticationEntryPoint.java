package com.pluto.ndi.shared.security;

import java.io.IOException;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import com.pluto.ndi.shared.web.ProblemDetailResponseWriter;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

@Component
class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ProblemDetailResponseWriter problemDetailResponseWriter;

    RestAuthenticationEntryPoint(ProblemDetailResponseWriter problemDetailResponseWriter) {
        this.problemDetailResponseWriter = problemDetailResponseWriter;
    }

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authenticationException
    ) throws IOException, ServletException {
        problemDetailResponseWriter.write(request, response, SecurityErrorCode.UNAUTHENTICATED);
    }
}
