package com.pluto.ndi.organization;

import java.util.UUID;

/** 项目归属的有效省分与地市。 */
public record OrganizationLocation(UUID provinceId, UUID cityId) {
}
