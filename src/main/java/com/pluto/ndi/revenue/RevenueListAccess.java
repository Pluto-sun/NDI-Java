package com.pluto.ndi.revenue;

import java.util.UUID;

/** 向列表固定列配置提供台账读取权限校验。 */
public interface RevenueListAccess {
    void requireListRead(UUID actor);
}
