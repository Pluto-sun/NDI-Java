package com.pluto.ndi.projects.repository;

import java.util.UUID;
import com.pluto.ndi.projects.domain.entity.ProjectSourceRecordEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectSourceRecordRepository extends JpaRepository<ProjectSourceRecordEntity, UUID> {
    boolean existsBySourceSystemAndSourceRecordId(String sourceSystem, String sourceRecordId);
}
