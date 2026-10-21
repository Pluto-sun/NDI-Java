package com.pluto.ndi.shared.security;

import java.io.IOException;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import com.pluto.ndi.shared.web.ProblemDetailResponseWriter;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

@Component
class RestAccessDeniedHandler implements AccessDeniedHandler {

    private final ProblemDetailResponseWriter problemDetailResponseWriter;

    RestAccessDeniedHandler(ProblemDetailResponseWriter problemDetailResponseWriter) {
        this.problemDetailResponseWriter = problemDetailResponseWriter;
    }

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException
    ) throws IOException, ServletException {
        problemDetailResponseWriter.write(request, response,
                accessDeniedException instanceof org.springframework.security.web.csrf.CsrfException
                        ? SecurityErrorCode.CSRF_INVALID : SecurityErrorCode.FORBIDDEN);
    }
}
