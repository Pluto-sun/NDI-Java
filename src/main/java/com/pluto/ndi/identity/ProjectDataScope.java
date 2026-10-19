package com.pluto.ndi.identity;

import java.util.UUID;

/** 身份模块计算出的项目数据可读范围。 */
public record ProjectDataScope(Level level, UUID organizationId) {

    /** 数据范围的组织层级。 */
    public enum Level {
        PROVINCE,
        CITY
    }
}
