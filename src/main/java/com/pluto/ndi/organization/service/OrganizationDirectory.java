package com.pluto.ndi.organization.service;

import java.util.Optional;
import java.util.UUID;

import com.pluto.ndi.organization.domain.OrganizationLocation;

/** 向业务模块提供组织归属校验，不暴露组织持久化模型。 */
public interface OrganizationDirectory {

    Optional<OrganizationLocation> findEnabledLocation(UUID provinceId, UUID cityId);
}
