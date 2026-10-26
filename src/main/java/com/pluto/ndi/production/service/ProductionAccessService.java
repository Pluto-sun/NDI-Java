package com.pluto.ndi.production.service;

import com.pluto.ndi.identity.service.*;
import com.pluto.ndi.production.domain.ProductionError;
import com.pluto.ndi.shared.web.BusinessException;

import org.springframework.stereotype.Service;

import java.util.*;

/** 排产权限复用账号组织目录，不使用文本接口人字段授权。 */
@Service
public class ProductionAccessService {
    private final IdentityDirectory identities;

    public ProductionAccessService(IdentityDirectory identities) {
        this.identities = identities;
    }

    public record Access(ProjectDataScope scope, boolean writable) {
        public boolean contains(UUID provinceId, UUID cityId) {
            return switch (scope.level()) {
                case NATIONAL -> true;
                case PROVINCE -> scope.provinceIds().contains(provinceId);
                case CITY -> scope.organizationId().equals(cityId);
            };
        }
    }

    public Access require(UUID actorId) {
        var actor =
                identities
                        .findProjectActor(actorId)
                        .filter(ProjectActor::enabled)
                        .orElseThrow(() -> new BusinessException(ProductionError.forbidden()));
        var user =
                identities
                        .findProjectUser(actorId)
                        .orElseThrow(() -> new BusinessException(ProductionError.forbidden()));
        var scope =
                identities
                        .findReadableProjectScope(actor)
                        .orElseThrow(() -> new BusinessException(ProductionError.forbidden()));
        var writers =
                Set.of(
                        "SYSTEM_ADMIN",
                        "INTERFACE_REVIEWER",
                        "PROVINCE_REPORTER",
                        "PROVINCE_DIGITAL_LEADER",
                        "PROVINCE_INTELLIGENT_LEADER",
                        "CITY_REPORTER");
        return new Access(scope, user.roleCodes().stream().anyMatch(writers::contains));
    }

    public Access maintain(UUID actorId, UUID province, UUID city) {
        var a = require(actorId);
        if (!a.writable() || !a.contains(province, city))
            throw new BusinessException(ProductionError.forbidden());
        return a;
    }
}
