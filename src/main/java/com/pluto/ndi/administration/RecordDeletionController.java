package com.pluto.ndi.administration;

import com.pluto.ndi.capabilities.GewuDeploymentService;
import com.pluto.ndi.capabilities.IntelligenceKind;
import com.pluto.ndi.capabilities.IntelligenceService;
import com.pluto.ndi.identity.service.IdentityDirectory;
import com.pluto.ndi.identity.service.ProjectActor;
import com.pluto.ndi.production.ProductionRecordDeletion;
import com.pluto.ndi.revenue.RevenueRecordDeletion;
import com.pluto.ndi.shared.security.ActorPrincipal;
import com.pluto.ndi.shared.web.ApiResponse;
import com.pluto.ndi.shared.web.ApiResponseFactory;
import com.pluto.ndi.shared.web.BusinessException;
import com.pluto.ndi.shared.web.ErrorCode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/record-deletions")
class RecordDeletionController {
    private final IdentityDirectory identities;
    private final RevenueRecordDeletion revenue;
    private final ProductionRecordDeletion production;
    private final GewuDeploymentService gewu;
    private final IntelligenceService intelligence;
    private final ApiResponseFactory responses;

    RecordDeletionController(IdentityDirectory identities, RevenueRecordDeletion revenue,
            ProductionRecordDeletion production, GewuDeploymentService gewu,
            IntelligenceService intelligence, ApiResponseFactory responses) {
        this.identities = identities;
        this.revenue = revenue;
        this.production = production;
        this.gewu = gewu;
        this.intelligence = intelligence;
        this.responses = responses;
    }

    enum Register { REVENUE, PRODUCTION, GEWU, WANGWU, AGENTS_LIVE, AGENTS_DEVELOPMENT }

    record Selected(@NotNull UUID id, @Min(0) long version) {}
    record Batch(@NotEmpty @Size(max = 100) List<@Valid @NotNull Selected> records) {}

    @PostMapping("/{register}")
    @Transactional
    public ApiResponse<Integer> delete(Authentication authentication, @PathVariable Register register,
            @Valid @RequestBody Batch input) {
        if (authentication == null || !(authentication.getPrincipal() instanceof ActorPrincipal principal))
            throw forbidden();
        UUID actor = principal.userId();
        if (identities.findProjectActor(actor).filter(ProjectActor::enabled).isEmpty()
                || identities.findProjectUser(actor).filter(user -> user.roleCodes().contains("SYSTEM_ADMIN")).isEmpty())
            throw forbidden();
        if (input.records().stream().map(Selected::id).distinct().count() != input.records().size())
            throw new BusinessException(new DeletionError(HttpStatus.BAD_REQUEST, "BATCH_DELETE_INVALID", "选中的记录不能重复"));

        // 固定顺序加锁，任一记录校验失败时整批回滚。
        for (var record : input.records().stream().sorted(Comparator.comparing(Selected::id)).toList()) {
            switch (register) {
                case REVENUE -> revenue.delete(actor, record.id(), record.version());
                case PRODUCTION -> production.delete(actor, record.id(), record.version());
                case GEWU -> gewu.delete(actor, record.id(), record.version());
                case WANGWU, AGENTS_LIVE, AGENTS_DEVELOPMENT ->
                        intelligence.delete(actor, IntelligenceKind.valueOf(register.name()), record.id(), record.version());
            }
        }
        return responses.success(input.records().size());
    }

    private BusinessException forbidden() {
        return new BusinessException(new DeletionError(
                HttpStatus.FORBIDDEN, "BATCH_DELETE_FORBIDDEN", "仅系统管理员可批量删除"));
    }

    private record DeletionError(HttpStatus status, String code, String message) implements ErrorCode {
        @Override
        public String title() {
            return message;
        }
    }
}
