package com.pluto.ndi.projects.repository;

import java.util.UUID;
import com.pluto.ndi.projects.domain.entity.ProjectImportRowRevisionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectImportRowRevisionRepository extends JpaRepository<ProjectImportRowRevisionEntity, UUID> { }
