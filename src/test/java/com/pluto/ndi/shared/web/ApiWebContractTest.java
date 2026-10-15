package com.pluto.ndi.shared.web;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import com.fasterxml.jackson.databind.SerializationFeature;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

class ApiWebContractTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        var clock = Clock.fixed(Instant.parse("2026-10-15T08:00:00Z"), ZoneOffset.UTC);
        var validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        var jsonConverter = new MappingJackson2HttpMessageConverter(Jackson2ObjectMapperBuilder.json()
                .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build());
        mockMvc = MockMvcBuilders.standaloneSetup(new ContractController(new ApiResponseFactory(clock)))
                .setControllerAdvice(new ApiExceptionHandler(new ProblemDetailFactory()))
                .setValidator(validator)
                .setMessageConverters(jsonConverter)
                .addFilters(new RequestCorrelationFilter())
                .build();
    }

    @Test
    void returnsExplicitSuccessEnvelopeWithCorrelatedRequestId() throws Exception {
        mockMvc.perform(get("/contracts/success").header("X-Request-Id", "request-123"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-Id", "request-123"))
                .andExpect(jsonPath("$.code", is("OK")))
                .andExpect(jsonPath("$.message", is("成功")))
                .andExpect(jsonPath("$.data.name", is("NDI")))
                .andExpect(jsonPath("$.requestId", is("request-123")))
                .andExpect(jsonPath("$.timestamp", is("2026-10-15T08:00:00Z")));
    }

    @Test
    void returnsFieldErrorsWithoutEchoingRejectedValue() throws Exception {
        mockMvc.perform(post("/contracts/validate")
                        .header("X-Request-Id", "validation-123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("X-Request-Id", "validation-123"))
                .andExpect(jsonPath("$.code", is("COMMON_VALIDATION_FAILED")))
                .andExpect(jsonPath("$.requestId", is("validation-123")))
                .andExpect(jsonPath("$.errors[0].field", is("name")))
                .andExpect(jsonPath("$.errors[0].message", is("名称不得为空")));
    }

    @Test
    void returnsModuleOwnedBusinessError() throws Exception {
        mockMvc.perform(get("/contracts/missing").header("X-Request-Id", "business-123"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is("PROJECT_NOT_FOUND")))
                .andExpect(jsonPath("$.requestId", is("business-123")))
                .andExpect(jsonPath("$.detail", is("项目不存在")));
    }

    @Test
    void returnsStableTypeMismatchError() throws Exception {
        mockMvc.perform(get("/contracts/numbers/not-a-number").header("X-Request-Id", "type-123"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("COMMON_TYPE_MISMATCH")))
                .andExpect(jsonPath("$.requestId", is("type-123")));
    }

    @Test
    void hidesUnhandledExceptionDetails() throws Exception {
        mockMvc.perform(get("/contracts/unexpected").header("X-Request-Id", "unexpected-123"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code", is("COMMON_INTERNAL_ERROR")))
                .andExpect(jsonPath("$.detail", is("系统暂时无法处理该请求")))
                .andExpect(jsonPath("$.requestId", is("unexpected-123")));
    }

    @RestController
    @RequestMapping("/contracts")
    static class ContractController {

        private final ApiResponseFactory responseFactory;

        ContractController(ApiResponseFactory responseFactory) {
            this.responseFactory = responseFactory;
        }

        @GetMapping("/success")
        ApiResponse<NameResponse> success() {
            return responseFactory.success(new NameResponse("NDI"));
        }

        @PostMapping("/validate")
        ApiResponse<NameResponse> validate(@Valid @RequestBody NameRequest request) {
            return responseFactory.success(new NameResponse(request.name()));
        }

        @GetMapping("/missing")
        ApiResponse<Void> missing() {
            throw new BusinessException(ProjectErrorCode.NOT_FOUND);
        }

        @GetMapping("/numbers/{value}")
        ApiResponse<Integer> number(@PathVariable Integer value) {
            return responseFactory.success(value);
        }

        @GetMapping("/unexpected")
        ApiResponse<Void> unexpected() {
            throw new IllegalStateException("internal implementation detail");
        }
    }

    record NameRequest(@NotBlank(message = "名称不得为空") String name) {
    }

    record NameResponse(String name) {
    }

    enum ProjectErrorCode implements ErrorCode {
        NOT_FOUND;

        @Override
        public org.springframework.http.HttpStatus status() {
            return org.springframework.http.HttpStatus.NOT_FOUND;
        }

        @Override
        public String code() {
            return "PROJECT_NOT_FOUND";
        }

        @Override
        public String title() {
            return "项目不存在";
        }

        @Override
        public String message() {
            return "项目不存在";
        }
    }
}
