package com.pluto.ndi.projects.mapper;

import com.pluto.ndi.identity.service.ProjectDataScope;
import com.pluto.ndi.projects.domain.entity.ProjectApprovalRequestEntity;
import com.pluto.ndi.projects.domain.entity.ProjectEntity;
import org.springframework.data.jpa.domain.Specification;

/** 审批列表与所属项目采用当前相同的数据范围，不以曾经参与申请代替数据授权。 */
public final class ProjectApprovalSpecifications {
    private ProjectApprovalSpecifications() { }

    public static Specification<ProjectApprovalRequestEntity> readable(ProjectDataScope scope) {
        return (request, query, builder) -> {
            var project = query.from(ProjectEntity.class);
            var authorized = switch (scope.level()) {
                case NATIONAL -> builder.conjunction();
                case PROVINCE -> project.get("provinceId").in(scope.provinceIds());
                case CITY -> builder.equal(project.get("cityId"), scope.organizationId());
            };
            return builder.and(builder.equal(project.get("id"), request.get("projectId")), authorized);
        };
    }
}
