package com.pluto.ndi.revenue;

import java.util.UUID;

/** 计收记录的版本化删除入口。 */
public interface RevenueRecordDeletion {
    void delete(UUID actor, UUID id, long version);
}
