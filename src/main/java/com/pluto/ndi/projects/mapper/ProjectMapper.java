package com.pluto.ndi.projects.mapper;

import java.util.List;

import com.pluto.ndi.projects.domain.entity.ProjectEntity;
import com.pluto.ndi.projects.service.ProjectDetailSummary;
import com.pluto.ndi.projects.service.ProjectSummary;
import org.springframework.stereotype.Component;

/** 负责项目领域对象与接口摘要之间的转换。 */
@Component
public class ProjectMapper {

    public ProjectSummary toSummary(ProjectEntity project, List<ProjectSummary.CategoryAmount> categories,
            String provinceLeaderName) {
        return toSummary(project,categories,provinceLeaderName,List.of());
    }
    public ProjectSummary toSummary(ProjectEntity project, List<ProjectSummary.CategoryAmount> categories,
            String provinceLeaderName, List<com.pluto.ndi.projects.domain.ProjectResponsiblePerson> people) {
        return new ProjectSummary(
                project.id(),
                project.projectCode(),
                project.projectName(),
                project.customerName(),
                project.industryCode(),
                project.totalAmount(),
                project.currentStageCode(),
                categories,
                project.createdAt(), project.opportunityDate(), project.importanceCode(),
                project.provinceId(), project.cityId(), project.provinceLeaderUserId(), provinceLeaderName,
                project.signingSubjectCode(), project.attributionMonth(), people
        );
    }

    public ProjectDetailSummary toDetail(ProjectEntity project, List<ProjectSummary.CategoryAmount> categories,
            String provinceLeaderName) {
        return toDetail(project,categories,provinceLeaderName,List.of());
    }
    public ProjectDetailSummary toDetail(ProjectEntity project, List<ProjectSummary.CategoryAmount> categories,
            String provinceLeaderName,List<com.pluto.ndi.projects.domain.ProjectResponsiblePerson> people) {
        return new ProjectDetailSummary(
                project.id(), project.projectCode(), project.provinceId(), project.cityId(),
                project.projectName(), project.customerName(), project.industryCode(), project.customerNeed(),
                project.constructionContentRaw(), project.productPromotionContent(), project.remark(),
                project.totalAmount(), project.contractNo(), project.signedAmount(), project.currentStageCode(),
                project.provinceLeaderUserId(), project.cityAccountManagerName(), project.signingSubjectCode(),
                project.productSupportExpertNames(),
                project.plannedBidMonth(), project.applicationScenarioCodes(),
                project.versionNo(), categories,
                project.createdAt(), project.updatedAt(), project.opportunityDate(), project.importanceCode(),
                project.attributionMonth(), provinceLeaderName, project.supportIndustryExpert(),people
        );
    }
}
