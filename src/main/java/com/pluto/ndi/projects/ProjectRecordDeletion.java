package com.pluto.ndi.projects;

import java.util.UUID;

public interface ProjectRecordDeletion {
    boolean deleteUnlessPending(UUID actor, UUID id, long version);
}
