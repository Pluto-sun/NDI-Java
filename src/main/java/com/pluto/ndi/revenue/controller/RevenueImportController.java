package com.pluto.ndi.revenue.controller;

import com.pluto.ndi.revenue.domain.RevenueError;
import com.pluto.ndi.revenue.service.*;
import com.pluto.ndi.revenue.util.RevenueExcel;
import com.pluto.ndi.shared.imports.ImportControllerBase;
import com.pluto.ndi.shared.security.ActorPrincipal;
import com.pluto.ndi.shared.web.*;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/revenue/entries")
public class RevenueImportController extends ImportControllerBase {
    private final RevenueService entries;
    private final RevenueAccessService access;

    public RevenueImportController(
            RevenueImportService imports,
            RevenueService entries,
            RevenueAccessService access,
            ApiResponseFactory responses) {
        super(imports, responses);
        this.entries = entries;
        this.access = access;
    }

    protected UUID actor(Authentication auth) {
        if (auth == null || !(auth.getPrincipal() instanceof ActorPrincipal actor))
            throw new BusinessException(RevenueError.forbidden());
        return actor.userId();
    }

    protected byte[] templateBytes(UUID actor) throws java.io.IOException {
        access.create(actor);
        return RevenueExcel.template(entries.reference(actor));
    }
}
