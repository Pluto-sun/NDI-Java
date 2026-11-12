package com.pluto.ndi.projects.controller;

import com.pluto.ndi.projects.domain.ProjectErrorCode;
import com.pluto.ndi.projects.service.HistoricalSnapshotImport;
import com.pluto.ndi.shared.excel.ExcelWorkbook;
import com.pluto.ndi.shared.imports.ImportControllerBase;
import com.pluto.ndi.shared.security.ActorPrincipal;
import com.pluto.ndi.shared.web.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/v1/admin/historical-snapshots")
public class HistoricalSnapshotImportController extends ImportControllerBase {
    private final HistoricalSnapshotImport imports;
    private final ApiResponseFactory responses;

    public HistoricalSnapshotImportController(HistoricalSnapshotImport imports, ApiResponseFactory responses) {
        super(imports, responses);
        this.imports = imports;
        this.responses = responses;
    }

    @Override
    protected UUID actor(Authentication auth) {
        if (auth == null || !(auth.getPrincipal() instanceof ActorPrincipal actor))
            throw new BusinessException(ProjectErrorCode.ACCESS_FORBIDDEN);
        imports.authorize(actor.userId());
        return actor.userId();
    }

    @Override
    protected byte[] templateBytes(UUID actor) throws java.io.IOException {
        return ExcelWorkbook.template(imports.fields(), Set.of("快照月份", "系统项目编号"));
    }

    @PostMapping("/complete")
    public ApiResponse<Void> complete(Authentication auth, @RequestParam String month) {
        imports.completeMonth(actor(auth), month);
        return responses.success(null);
    }

    @GetMapping("/old-revisions")
    public ApiResponse<List<Map<String, Object>>> oldRevisions(Authentication auth) {
        return responses.success(imports.oldRevisions(actor(auth)));
    }
}
