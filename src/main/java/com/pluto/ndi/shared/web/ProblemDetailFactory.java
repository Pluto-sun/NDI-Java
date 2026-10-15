package com.pluto.ndi.shared.web;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;

@Component
public class ProblemDetailFactory {

    public ProblemDetail create(ErrorCode errorCode, URI instance) {
        return create(errorCode, instance, List.of());
    }

    public ProblemDetail create(ErrorCode errorCode, URI instance, List<FieldViolation> errors) {
        var problem = ProblemDetail.forStatusAndDetail(errorCode.status(), errorCode.message());
        problem.setTitle(errorCode.title());
        problem.setInstance(instance);
        problem.setProperty("code", errorCode.code());
        problem.setProperty("requestId", RequestCorrelationFilter.currentRequestId().orElseGet(() -> UUID.randomUUID().toString()));
        if (!errors.isEmpty()) {
            problem.setProperty("errors", List.copyOf(errors));
        }
        return problem;
    }
}
