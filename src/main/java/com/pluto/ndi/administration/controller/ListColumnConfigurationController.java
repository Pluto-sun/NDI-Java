package com.pluto.ndi.administration.controller;

import com.pluto.ndi.administration.domain.ListColumnCatalog;
import com.pluto.ndi.administration.service.ListColumnConfigurationService;
import com.pluto.ndi.shared.security.SecurityErrorCode;
import com.pluto.ndi.shared.security.ActorPrincipal;
import com.pluto.ndi.shared.web.ApiResponse;
import com.pluto.ndi.shared.web.ApiResponseFactory;
import com.pluto.ndi.shared.web.BusinessException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class ListColumnConfigurationController {
    private final ListColumnConfigurationService configs;
    private final ApiResponseFactory responses;

    public ListColumnConfigurationController(ListColumnConfigurationService configs, ApiResponseFactory responses) {
        this.configs = configs;
        this.responses = responses;
    }

    public record Request(List<String> fixedFields) {}

    @GetMapping("/list-columns/{list}")
    public ApiResponse<ListColumnConfigurationService.Fixed> read(Authentication auth, @PathVariable ListColumnCatalog list) {
        return responses.success(configs.read(actor(auth), list));
    }

    @GetMapping("/admin/list-columns")
    public ApiResponse<ListColumnConfigurationService.Catalog> manage(Authentication auth) {
        return responses.success(configs.manage(actor(auth)));
    }

    @PutMapping("/admin/list-columns/{list}")
    public ApiResponse<ListColumnConfigurationService.Fixed> save(Authentication auth, @PathVariable ListColumnCatalog list,
            @RequestBody Request request) {
        return responses.success(configs.save(actor(auth), list, request.fixedFields()));
    }

    private UUID actor(Authentication auth) {
        if (auth == null || !(auth.getPrincipal() instanceof ActorPrincipal actor))
            throw new BusinessException(SecurityErrorCode.UNAUTHENTICATED);
        return actor.userId();
    }
}
