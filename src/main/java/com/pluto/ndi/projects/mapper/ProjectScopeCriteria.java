package com.pluto.ndi.projects.mapper;

import com.pluto.ndi.identity.service.ProjectDataScope;
import com.pluto.ndi.projects.domain.entity.ProjectEntity;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

/** 待办等关联读模型与项目主档使用一致的范围谓词。 */
public final class ProjectScopeCriteria {
    private ProjectScopeCriteria() { }
    public static Predicate predicate(ProjectDataScope scope, Root<ProjectEntity> project, CriteriaBuilder builder) {
        return switch (scope.level()) {
            case NATIONAL -> builder.conjunction();
            case PROVINCE -> project.get("provinceId").in(scope.provinceIds());
            case CITY -> builder.equal(project.get("cityId"), scope.organizationId());
        };
    }
}
