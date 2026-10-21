package com.pluto.ndi.projects.domain;

import java.time.Instant;
import java.util.UUID;

/** 一个项目在同一活动修订号下的完整督办周期。 */
public record ProjectInactivityCase(
        UUID id, UUID projectId, String projectCode, String projectName, UUID provinceId, UUID cityId,
        long activityRevision, Instant lastActivityAt, String status, Instant provinceSentAt,
        Instant interfaceSentAt, Instant groupSentAt, Instant createdAt, Instant closedAt, boolean pending
) {
}
