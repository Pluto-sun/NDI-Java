package com.pluto.ndi.identity;

import java.util.UUID;

/** 项目模块完成权限判断所需的最小账号快照。 */
public record ProjectActor(
        UUID id,
        UUID provinceId,
        UUID cityId,
        boolean enabled
) {
}
