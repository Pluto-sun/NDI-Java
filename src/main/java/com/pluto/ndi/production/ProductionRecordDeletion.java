package com.pluto.ndi.production;

import java.util.UUID;

/** 排产记录的版本化删除入口。 */
public interface ProductionRecordDeletion {
    void delete(UUID actor, UUID id, long version);
}
