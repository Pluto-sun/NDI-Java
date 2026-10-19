package com.pluto.ndi.projects.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectIndustryReferenceRepository extends JpaRepository<ProjectIndustryReferenceEntity, String> {

    boolean existsByIndustryCodeAndEnabledTrue(String industryCode);
}
