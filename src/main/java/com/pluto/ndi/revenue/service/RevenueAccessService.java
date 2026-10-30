package com.pluto.ndi.revenue.service;

import com.pluto.ndi.identity.service.*;
import com.pluto.ndi.revenue.domain.RevenueError;
import com.pluto.ndi.shared.web.BusinessException;

import org.springframework.stereotype.Service;

import java.util.*;

/** 省市均按本省办理；接口人全国查看，但仅在负责省份及具体组织内认定。 */
@Service
public class RevenueAccessService {
    private final IdentityDirectory identities;

    public RevenueAccessService(IdentityDirectory identities) {
        this.identities = identities;
    }

    public record Access(
            ProjectUser user, boolean national, boolean creator, boolean reviewer, boolean local) {
        public boolean readable(UUID province, boolean organization) {
            return national
                    || !organization && local && Objects.equals(user.provinceId(), province);
        }

        public boolean recognize(UUID province, boolean organization) {
            if (creator) return false;
            return reviewer
                    ? organization || user.reviewerProvinceIds().contains(province)
                    : !organization && local && Objects.equals(user.provinceId(), province);
        }
    }

    public Access require(UUID actor) {
        identities
                .findProjectActor(actor)
                .filter(ProjectActor::enabled)
                .orElseThrow(RevenueAccessService::forbidden);
        var user = identities.findProjectUser(actor).orElseThrow(RevenueAccessService::forbidden);
        var roles = user.roleCodes();
        boolean creator =
                roles.stream()
                        .anyMatch(Set.of("SYSTEM_ADMIN", "GROUP_REVIEWER", "DS_TECH")::contains);
        boolean reviewer = !creator && roles.contains("INTERFACE_REVIEWER");
        boolean local =
                roles.stream()
                        .anyMatch(
                                Set.of(
                                                "PROVINCE_REPORTER",
                                                "PROVINCE_DIGITAL_LEADER",
                                                "PROVINCE_INTELLIGENT_LEADER",
                                                "CITY_REPORTER")
                                        ::contains);
        boolean national =
                creator
                        || reviewer
                        || roles.stream().anyMatch(Set.of("DS_INTELLIGENCE", "GD_DPASS")::contains);
        if (!national && !local) throw forbidden();
        return new Access(user, national, creator, reviewer, local);
    }

    public void create(UUID actor) {
        if (!require(actor).creator()) throw forbidden();
    }

    public static BusinessException forbidden() {
        return new BusinessException(RevenueError.forbidden());
    }
}
