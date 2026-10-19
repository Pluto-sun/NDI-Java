package com.pluto.ndi.projects.mapper;

import java.util.List;

import com.pluto.ndi.projects.domain.entity.ProjectEntity;
import com.pluto.ndi.projects.service.ProjectSummary;
import org.springframework.stereotype.Component;

/** 负责项目领域对象与接口摘要之间的转换。 */
@Component
public class ProjectMapper {

    public ProjectSummary toSummary(ProjectEntity project, List<ProjectSummary.CategoryAmount> categories) {
        return new ProjectSummary(
                project.id(),
                project.projectCode(),
                project.projectName(),
                project.customerName(),
                project.industryCode(),
                project.totalAmount(),
                project.currentStageCode(),
                categories,
                project.createdAt()
        );
    }
}
