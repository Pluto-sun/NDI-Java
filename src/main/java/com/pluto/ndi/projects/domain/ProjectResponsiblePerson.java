package com.pluto.ndi.projects.domain;

import java.util.UUID;

/** 项目内职责标签；账号关联不授予任何业务权限。 */
public record ProjectResponsiblePerson(
        UUID id,
        String name,
        String responsibility,
        UUID userId,
        String accountName,
        boolean accountEnabled) {}
