package com.pluto.ndi.capabilities;

import com.pluto.ndi.shared.security.ActorPrincipal;
import com.pluto.ndi.shared.web.ApiResponse;
import com.pluto.ndi.shared.web.ApiResponseFactory;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
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

    @PostMapping("/failed-rows/original")
    public ResponseEntity<byte[]> originalFailedRows(Authentication auth, @RequestPart MultipartFile file,
            @RequestParam int year, @RequestParam String sheet, @RequestParam String fingerprint)
            throws IOException {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("月度 Token 导入预检失败原始数据.xlsx", StandardCharsets.UTF_8)
                        .build().toString())
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(imports.originalFailedRows(actor(auth), file.getBytes(), sheet, year, fingerprint));
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
