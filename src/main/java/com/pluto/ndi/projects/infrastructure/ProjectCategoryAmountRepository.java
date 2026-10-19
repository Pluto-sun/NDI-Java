package com.pluto.ndi.projects.infrastructure;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectCategoryAmountRepository extends JpaRepository<ProjectCategoryAmountEntity, UUID> {

    List<ProjectCategoryAmountEntity> findByProjectIdIn(Collection<UUID> projectIds);
}
