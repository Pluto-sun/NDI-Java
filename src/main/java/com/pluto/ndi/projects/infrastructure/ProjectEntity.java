package com.pluto.ndi.projects.infrastructure;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.pluto.ndi.projects.domain.ProjectStage;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "project_project")
public class ProjectEntity {

    @Id
    private UUID id;

    @Column(name = "project_code", nullable = false, unique = true)
    private String projectCode;

    @Column(name = "province_id", nullable = false)
    private UUID provinceId;

    @Column(name = "city_id", nullable = false)
    private UUID cityId;

    @Column(name = "project_name", nullable = false)
    private String projectName;

    @Column(name = "customer_name", nullable = false)
    private String customerName;

    @Column(name = "industry_code", nullable = false)
    private String industryCode;

    @Column(name = "customer_need", nullable = false)
    private String customerNeed;

    @Column(name = "total_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "current_stage_code", nullable = false)
    private String currentStageCode;

    @Column(name = "province_leader_user_id", nullable = false)
    private UUID provinceLeaderUserId;

    @Column(name = "city_account_manager_name", nullable = false)
    private String cityAccountManagerName;

    @Version
    @Column(name = "version_no", nullable = false)
    private long versionNo;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_by", nullable = false)
    private UUID updatedBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ProjectEntity() {
    }

    public static ProjectEntity create(
            UUID id,
            String projectCode,
            UUID provinceId,
            UUID cityId,
            String projectName,
            String customerName,
            String industryCode,
            String customerNeed,
            BigDecimal totalAmount,
            UUID provinceLeaderUserId,
            String cityAccountManagerName,
            UUID actorId,
            Instant occurredAt
    ) {
        var entity = new ProjectEntity();
        entity.id = id;
        entity.projectCode = projectCode;
        entity.provinceId = provinceId;
        entity.cityId = cityId;
        entity.projectName = projectName;
        entity.customerName = customerName;
        entity.industryCode = industryCode;
        entity.customerNeed = customerNeed;
        entity.totalAmount = totalAmount;
        entity.currentStageCode = ProjectStage.SOLUTION_EXCHANGE.name();
        entity.provinceLeaderUserId = provinceLeaderUserId;
        entity.cityAccountManagerName = cityAccountManagerName;
        entity.createdBy = actorId;
        entity.createdAt = occurredAt;
        entity.updatedBy = actorId;
        entity.updatedAt = occurredAt;
        return entity;
    }

    public UUID id() {
        return id;
    }

    public String projectCode() {
        return projectCode;
    }

    public UUID provinceId() {
        return provinceId;
    }

    public UUID cityId() {
        return cityId;
    }

    public String projectName() {
        return projectName;
    }

    public String customerName() {
        return customerName;
    }

    public String industryCode() {
        return industryCode;
    }

    public BigDecimal totalAmount() {
        return totalAmount;
    }

    public String currentStageCode() {
        return currentStageCode;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public long versionNo() {
        return versionNo;
    }
}
