package com.pluto.ndi.identity.service;

import java.util.Optional;
import java.util.UUID;
import java.util.List;


/** 向项目模块提供账号状态、角色和省分负责人资格。 */
public interface IdentityDirectory {

    Optional<ProjectActor> findProjectActor(UUID userId);

    Optional<ProjectUser> findProjectUser(UUID userId);

    List<ProjectUser> findEnabledProjectUsers();

    List<ProjectUser> findEligibleProvinceLeaders(UUID provinceId);

    List<ProjectUser> findEligibleStageApprovers(UUID provinceId);

    List<ProjectUser> findEligibleMajorChangeApprovers(UUID provinceId, ProjectInitiatorLevel initiatorLevel);

    boolean canCreateProject(ProjectActor actor, UUID provinceId, UUID cityId);

    boolean canSaveProjectDraft(ProjectActor actor);

    Optional<ProjectDataScope> findReadableProjectScope(ProjectActor actor);

    ProjectApprovalScope findApprovalScope(UUID actorId);

    boolean isEligibleProvinceLeader(UUID userId, UUID provinceId);

    Optional<UUID> findEligibleProvinceLeaderByName(String displayName, UUID provinceId);

    /** 按当前目录姓名返回匹配账号；不排除停用账号，避免隐藏既有项目责任信息。 */
    List<UUID> findUserIdsByDisplayNameContaining(String displayName);

    /** 批量读取当前责任目录姓名，包含停用账号。 */
    java.util.Map<UUID, String> findDisplayNames(java.util.Collection<UUID> userIds);

    boolean canImportProject(ProjectActor actor, UUID provinceId, UUID cityId);

    boolean canInitiateMajorChange(ProjectActor actor, UUID provinceId, UUID cityId, ProjectInitiatorLevel level);

    boolean isEligibleMajorChangeApprover(UUID userId, UUID provinceId, ProjectInitiatorLevel initiatorLevel);

    boolean canAdvanceProjectStage(ProjectActor actor, UUID provinceId, UUID cityId, ProjectInitiatorLevel level);

    boolean isEligibleStageApprover(UUID userId, UUID provinceId);

    boolean canUploadProjectDocument(ProjectActor actor, UUID provinceId, UUID cityId);

    boolean canAppendProjectProgress(ProjectActor actor, UUID provinceId, UUID cityId);

    boolean canUpdateProjectProfile(ProjectActor actor, UUID provinceId, UUID cityId);

    List<ProjectUser> findProjectReminderRecipients(UUID provinceId, UUID assignedLeaderId,
            ProjectReminderAudience audience);

    Optional<ProjectReminderForwarding> findProjectReminderForwarding(UUID actorId, UUID provinceId, UUID cityId);

    boolean isSystemAdministrator(UUID actorId);
}
