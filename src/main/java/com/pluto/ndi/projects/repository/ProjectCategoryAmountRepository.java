package com.pluto.ndi.projects.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import com.pluto.ndi.projects.domain.entity.ProjectCategoryAmountEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectCategoryAmountRepository extends JpaRepository<ProjectCategoryAmountEntity, UUID> {

    List<ProjectCategoryAmountEntity> findByProjectIdIn(Collection<UUID> projectIds);
}
