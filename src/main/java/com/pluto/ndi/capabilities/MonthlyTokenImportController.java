package com.pluto.ndi.capabilities;

import com.pluto.ndi.shared.security.ActorPrincipal;
import com.pluto.ndi.shared.web.ApiResponse;
import com.pluto.ndi.shared.web.ApiResponseFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/capability/agents-live/token-import")
class MonthlyTokenImportController {
    private final MonthlyTokenImport imports;
    private final ApiResponseFactory responses;

    MonthlyTokenImportController(MonthlyTokenImport imports, ApiResponseFactory responses) {
        this.imports = imports;
        this.responses = responses;
    }

    @PostMapping("/preview")
    public ApiResponse<MonthlyTokenImport.Preview> preview(Authentication auth, @RequestPart MultipartFile file,
            @RequestParam int year, @RequestParam(required = false) String sheet) throws IOException {
        return responses.success(imports.preview(actor(auth), file.getBytes(), sheet, year));
    }

    @PostMapping("/confirm")
    public ApiResponse<Integer> confirm(Authentication auth, @RequestPart MultipartFile file,
            @RequestParam int year, @RequestParam String sheet, @RequestPart List<String> selection)
            throws IOException {
        return responses.success(imports.confirm(actor(auth), file.getBytes(), sheet, year, selection));
    }

    private UUID actor(Authentication auth) {
        if (auth == null || !(auth.getPrincipal() instanceof ActorPrincipal principal))
            throw GewuDeploymentService.failure(HttpStatus.FORBIDDEN, "无能力指标维护权限");
        return principal.userId();
    }
}
