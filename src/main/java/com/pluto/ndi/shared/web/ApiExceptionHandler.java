package com.pluto.ndi.shared.web;

import java.util.List;
import java.util.stream.Stream;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.ConstraintViolationException;
import org.apache.catalina.connector.ClientAbortException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.servlet.HandlerMapping;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApiExceptionHandler.class);

    private final ProblemDetailFactory problemDetailFactory;

    public ApiExceptionHandler(ProblemDetailFactory problemDetailFactory) {
        this.problemDetailFactory = problemDetailFactory;
    }

    @ExceptionHandler(BusinessException.class)
    ProblemDetail handleBusiness(BusinessException exception, HttpServletRequest request) {
        return problem(exception.errorCode(), request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail handleIllegalArgument(IllegalArgumentException exception, HttpServletRequest request) {
        return problem(CommonErrorCode.INVALID_ARGUMENT, request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleMethodArgumentNotValid(MethodArgumentNotValidException exception, HttpServletRequest request) {
        var errors = exception.getBindingResult().getFieldErrors().stream()
                .map(this::toFieldViolation)
                .toList();
        return problem(CommonErrorCode.VALIDATION_FAILED, request, errors);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ProblemDetail handleConstraintViolation(ConstraintViolationException exception, HttpServletRequest request) {
        var errors = exception.getConstraintViolations().stream()
                .map(violation -> new FieldViolation(lastPathSegment(violation.getPropertyPath().toString()), violation.getMessage()))
                .toList();
        return problem(CommonErrorCode.VALIDATION_FAILED, request, errors);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    ProblemDetail handleHandlerMethodValidation(HandlerMethodValidationException exception, HttpServletRequest request) {
        var errors = exception.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> new FieldViolation(
                                result.getMethodParameter().getParameterName(),
                                error.getDefaultMessage())))
                .toList();
        return problem(CommonErrorCode.VALIDATION_FAILED, request, errors);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ProblemDetail handleMessageNotReadable(HttpMessageNotReadableException exception, HttpServletRequest request) {
        return problem(CommonErrorCode.MALFORMED_REQUEST, request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException exception, HttpServletRequest request) {
        return problem(CommonErrorCode.TYPE_MISMATCH, request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ProblemDetail handleMethodNotSupported(HttpRequestMethodNotSupportedException exception, HttpServletRequest request) {
        return problem(CommonErrorCode.METHOD_NOT_ALLOWED, request);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ProblemDetail handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException exception, HttpServletRequest request) {
        return problem(CommonErrorCode.UNSUPPORTED_MEDIA_TYPE, request);
    }

    @ExceptionHandler(AuthenticationException.class)
    ProblemDetail handleAuthentication(AuthenticationException exception, HttpServletRequest request) {
        return problem(com.pluto.ndi.shared.security.SecurityErrorCode.UNAUTHENTICATED, request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail handleAccessDenied(AccessDeniedException exception, HttpServletRequest request) {
        return problem(com.pluto.ndi.shared.security.SecurityErrorCode.FORBIDDEN, request);
    }

    // 声明响应参数，让 Spring 将返回 null 视为已处理。
    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(
            Exception exception, HttpServletRequest request, HttpServletResponse response) {
        for (Throwable cause = exception; cause != null && cause != cause.getCause(); cause = cause.getCause()) {
            if (cause instanceof AsyncRequestNotUsableException || cause instanceof ClientAbortException) {
                // Servlet 响应已不可写，不能再尝试返回错误 JSON。
                LOGGER.debug("HTTP response unavailable, requestId={}, method={}, route={}, type={}",
                        RequestCorrelationFilter.currentRequestId().orElse("missing"),
                        request.getMethod(), request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE),
                        cause.getClass().getSimpleName());
                return null;
            }
        }
        LOGGER.error("Unhandled exception, requestId={}", RequestCorrelationFilter.currentRequestId().orElse("missing"), exception);
        return problem(CommonErrorCode.INTERNAL_ERROR, request);
    }

    private ProblemDetail problem(ErrorCode errorCode, HttpServletRequest request) {
        return problemDetailFactory.create(errorCode, RequestUri.from(request));
    }

    private ProblemDetail problem(ErrorCode errorCode, HttpServletRequest request, List<FieldViolation> errors) {
        return problemDetailFactory.create(errorCode, RequestUri.from(request), errors);
    }

    private FieldViolation toFieldViolation(FieldError error) {
        return new FieldViolation(error.getField(), error.getDefaultMessage());
    }

    private String lastPathSegment(String path) {
        return Stream.of(path.split("\\."))
                .reduce((first, second) -> second)
                .orElse(path);
    }
}
