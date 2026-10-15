package com.pluto.ndi.shared.web;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

@Component
public class ProblemDetailResponseWriter {

    private final ObjectMapper objectMapper;
    private final ProblemDetailFactory problemDetailFactory;

    public ProblemDetailResponseWriter(ObjectMapper objectMapper, ProblemDetailFactory problemDetailFactory) {
        this.objectMapper = objectMapper;
        this.problemDetailFactory = problemDetailFactory;
    }

    public void write(HttpServletRequest request, HttpServletResponse response, ErrorCode errorCode) throws IOException {
        var problem = problemDetailFactory.create(errorCode, RequestUri.from(request));
        response.setStatus(errorCode.status().value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), problem);
    }
}
