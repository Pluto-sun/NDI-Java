package com.pluto.ndi.revenue.service;

import com.pluto.ndi.identity.service.*;
import com.pluto.ndi.revenue.RevenueListAccess;
import com.pluto.ndi.revenue.domain.Revenue;
import com.pluto.ndi.revenue.domain.RevenueError;
import com.pluto.ndi.shared.web.BusinessException;

import org.springframework.stereotype.Service;

import java.util.*;

/** 省市可查看本省来源记录；接口人按既有办理范围发起认定。 */
@Service
public class RevenueAccessService implements RevenueListAccess {
    private final IdentityDirectory identities;

    public RevenueAccessService(IdentityDirectory identities) {
        this.identities = identities;
    }

    private static final Set<String> PROVINCE_ROLES = Set.of(
            "PROVINCE_REPORTER", "PROVINCE_DIGITAL_LEADER", "PROVINCE_INTELLIGENT_LEADER");

    public record Access(
            ProjectUser user, boolean national, boolean creator, boolean reviewer, boolean local) {
        public boolean readable(UUID province, boolean organization) {
            return national
                    || province != null
                            && !organization
                            && local
                            && Objects.equals(user.provinceId(), province);
        }

        public boolean readsAssignedOrganizations() {
            return user.provinceId() != null && user.roleCodes().stream().anyMatch(PROVINCE_ROLES::contains);
        }

        public boolean recognize(UUID province, boolean organization, String category) {
            return reviewer && Revenue.REVIEWED_CATEGORIES.contains(Objects.toString(category, ""))
                    && (organization || province == null || user.reviewerProvinceIds().contains(province));
        }

        public boolean groupApprover() {
            return user.roleCodes().contains("GROUP_REVIEWER");
        }

        public boolean administrator() {
            return user.roleCodes().contains("SYSTEM_ADMIN");
        }
    }

    @Override
    public void requireListRead(UUID actor) {
        require(actor);
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
        boolean reviewer = roles.contains("INTERFACE_REVIEWER");
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
