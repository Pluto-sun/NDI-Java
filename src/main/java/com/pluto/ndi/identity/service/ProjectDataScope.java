package com.pluto.ndi.identity.service;

import java.util.Objects;
import java.util.UUID;

/** 身份模块计算出的项目数据可读范围；全国范围不绑定具体组织。 */
public record ProjectDataScope(Level level, UUID organizationId) {

    public ProjectDataScope {
        Objects.requireNonNull(level, "项目数据范围层级不得为空");
        if ((level == Level.NATIONAL) != (organizationId == null)) {
            throw new IllegalArgumentException("全国范围不得绑定组织编号，省市范围必须指定组织编号");
        }
    }

    /** 数据范围的组织层级。 */
    public enum Level {
        NATIONAL,
        PROVINCE,
        CITY
    }
}
