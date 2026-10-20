package com.pluto.ndi.organization.service;

import java.util.UUID;

/** 对业务模块公开的有效项目属地标识。 */
public record OrganizationLocation(UUID provinceId, UUID cityId) { }
