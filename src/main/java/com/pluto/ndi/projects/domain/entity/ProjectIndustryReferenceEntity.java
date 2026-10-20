package com.pluto.ndi.projects.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "project_industry_reference")
public class ProjectIndustryReferenceEntity {

    @Id
    @Column(name = "industry_code", nullable = false)
    private String industryCode;

    @Column(name = "industry_name", nullable = false)
    private String industryName;

    @Column(nullable = false)
    private boolean enabled;

    protected ProjectIndustryReferenceEntity() {
    }

    public String industryCode() { return industryCode; }
    public String industryName() { return industryName; }
}
