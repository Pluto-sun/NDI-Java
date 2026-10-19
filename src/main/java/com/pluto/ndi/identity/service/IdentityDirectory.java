package com.pluto.ndi.identity.service;

import java.util.Optional;
import java.util.UUID;


/** 向项目模块提供账号状态、角色和省分负责人资格。 */
public interface IdentityDirectory {

    Optional<ProjectActor> findProjectActor(UUID userId);

    boolean canCreateProject(ProjectActor actor, UUID provinceId, UUID cityId);

    Optional<ProjectDataScope> findReadableProjectScope(ProjectActor actor);

    boolean isEligibleProvinceLeader(UUID userId, UUID provinceId);

    boolean canInitiateMajorChange(ProjectActor actor, UUID provinceId, UUID cityId, ProjectInitiatorLevel level);

    boolean isEligibleMajorChangeApprover(UUID userId, UUID provinceId, ProjectInitiatorLevel initiatorLevel);

    boolean canAdvanceProjectStage(ProjectActor actor, UUID provinceId, UUID cityId, ProjectInitiatorLevel level);

    boolean isEligibleStageApprover(UUID userId, UUID provinceId);

    boolean canUploadProjectDocument(ProjectActor actor, UUID provinceId, UUID cityId);
}
