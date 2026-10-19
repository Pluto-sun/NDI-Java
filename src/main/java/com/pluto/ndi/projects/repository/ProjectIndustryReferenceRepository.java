package com.pluto.ndi.projects.repository;

import com.pluto.ndi.projects.domain.entity.ProjectIndustryReferenceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectIndustryReferenceRepository extends JpaRepository<ProjectIndustryReferenceEntity, String> {

    boolean existsByIndustryCodeAndEnabledTrue(String industryCode);
}
