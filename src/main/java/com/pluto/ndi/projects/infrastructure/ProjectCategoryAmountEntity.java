package com.pluto.ndi.projects.infrastructure;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "project_category_amount")
public class ProjectCategoryAmountEntity {

    @Id
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "category_code", nullable = false)
    private String categoryCode;

    @Column(name = "expected_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal expectedAmount;

    protected ProjectCategoryAmountEntity() {
    }

    public static ProjectCategoryAmountEntity create(UUID projectId, String categoryCode, BigDecimal expectedAmount) {
        var entity = new ProjectCategoryAmountEntity();
        entity.id = UUID.randomUUID();
        entity.projectId = projectId;
        entity.categoryCode = categoryCode;
        entity.expectedAmount = expectedAmount;
        return entity;
    }

    public UUID projectId() {
        return projectId;
    }

    public String categoryCode() {
        return categoryCode;
    }

    public BigDecimal expectedAmount() {
        return expectedAmount;
    }
}
