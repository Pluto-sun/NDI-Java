package com.pluto.ndi.projects.domain.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import com.pluto.ndi.projects.domain.ProjectStage;
import com.pluto.ndi.projects.domain.ProjectNarrative;
import jakarta.persistence.Column;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
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

    @Column(name = "importance_code", nullable = false, length = 30)
    private String importanceCode = "NORMAL";

    @Column(name = "construction_content_raw", nullable = false)
    private String constructionContentRaw;

    @Column(name = "product_promotion_content")
    private String productPromotionContent;

    @Column(name = "remark")
    private String remark;

    @Column(name = "total_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "contract_no", unique = true, length = 100)
    private String contractNo;

    @Column(name = "signed_amount", precision = 18, scale = 2)
    private BigDecimal signedAmount;

    @Column(name = "current_stage_code", nullable = false)
    private String currentStageCode;

    @Column(name = "province_leader_user_id", nullable = false)
    private UUID provinceLeaderUserId;

    @Column(name = "city_account_manager_name", nullable = false)
    private String cityAccountManagerName;

    @Column(name = "signing_subject_code", nullable = false, length = 20)
    private String signingSubjectCode;

    @Column(name = "planned_bid_month")
    private LocalDate plannedBidMonth;

    @ElementCollection
    @CollectionTable(name = "project_application_scenario", joinColumns = @JoinColumn(name = "project_id"))
    @OrderColumn(name = "sort_order")
    @Column(name = "scenario_code", nullable = false, length = 40)
    private List<String> applicationScenarioCodes = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "project_support_expert", joinColumns = @JoinColumn(name = "project_id"))
    @OrderColumn(name = "sort_order")
    @Column(name = "expert_name", nullable = false, length = 100)
    private List<String> productSupportExpertNames = new ArrayList<>();

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

    @Column(name = "opportunity_date")
    private Instant opportunityDate;

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
            String signingSubjectCode,
            UUID actorId,
            Instant occurredAt
    ) {
        return create(id, projectCode, provinceId, cityId, projectName, customerName, industryCode,
                customerNeed, totalAmount, provinceLeaderUserId, cityAccountManagerName, signingSubjectCode,
                actorId, occurredAt, "NORMAL");
    }

    /** 仅在创建时指定重要性代码，后续普通资料维护不改写该属性。 */
    public static ProjectEntity create(UUID id, String projectCode, UUID provinceId, UUID cityId,
            String projectName, String customerName, String industryCode, String customerNeed,
            BigDecimal totalAmount, UUID provinceLeaderUserId, String cityAccountManagerName,
            String signingSubjectCode, UUID actorId, Instant occurredAt, String importanceCode) {
        var entity = new ProjectEntity();
        entity.importanceCode = importanceCode;
        entity.id = id;
        entity.projectCode = projectCode;
        entity.provinceId = provinceId;
        entity.cityId = cityId;
        entity.projectName = projectName;
        entity.customerName = customerName;
        entity.industryCode = industryCode;
        entity.customerNeed = customerNeed;
        entity.constructionContentRaw = customerNeed;
        entity.totalAmount = totalAmount;
        entity.currentStageCode = ProjectStage.SOLUTION_EXCHANGE.name();
        entity.provinceLeaderUserId = provinceLeaderUserId;
        entity.cityAccountManagerName = cityAccountManagerName;
        entity.signingSubjectCode = signingSubjectCode;
        entity.createdBy = actorId;
        entity.createdAt = occurredAt;
        entity.updatedBy = actorId;
        entity.updatedAt = occurredAt;
        entity.opportunityDate = occurredAt;
        return entity;
    }

    /** 历史导入保留原阶段与发生时间，不模拟在线审批。 */
    public static ProjectEntity imported(
            UUID id, String projectCode, UUID provinceId, UUID cityId, String projectName,
            String customerName, String industryCode, String customerNeed, BigDecimal totalAmount,
            UUID provinceLeaderUserId, String cityAccountManagerName, ProjectStage stage,
            String contractNo, BigDecimal signedAmount, String signingSubjectCode,
            UUID actorId, Instant createdAt
    ) {
        var entity = create(id, projectCode, provinceId, cityId, projectName, customerName,
                industryCode, customerNeed, totalAmount, provinceLeaderUserId,
                cityAccountManagerName, signingSubjectCode, actorId, createdAt);
        entity.currentStageCode = stage.name();
        entity.contractNo = contractNo;
        entity.signedAmount = signedAmount;
        return entity;
    }

    public UUID id() {
        return id;
    }

    public String importanceCode() { return importanceCode; }

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

    public String customerNeed() { return customerNeed; }
    public String constructionContentRaw() { return constructionContentRaw; }
    public String productPromotionContent() { return productPromotionContent; }
    public String remark() { return remark; }

    public void replaceNarrative(ProjectNarrative narrative) {
        constructionContentRaw = narrative.constructionContentRaw();
        productPromotionContent = narrative.productPromotionContent();
        remark = narrative.remark();
    }

    public String industryCode() {
        return industryCode;
    }

    public BigDecimal totalAmount() {
        return totalAmount;
    }

    public String signingSubjectCode() { return signingSubjectCode; }
    public LocalDate plannedBidMonth() { return plannedBidMonth; }
    public List<String> applicationScenarioCodes() { return List.copyOf(applicationScenarioCodes); }

    public void replacePlanningAttributes(LocalDate bidMonth, List<String> scenarioCodes) {
        plannedBidMonth = bidMonth;
        applicationScenarioCodes.clear();
        applicationScenarioCodes.addAll(scenarioCodes);
    }
    public List<String> productSupportExpertNames() { return List.copyOf(productSupportExpertNames); }

    public void replaceProductSupportExperts(List<String> names) {
        productSupportExpertNames.clear();
        productSupportExpertNames.addAll(names);
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

    public String contractNo() {
        return contractNo;
    }

    public BigDecimal signedAmount() {
        return signedAmount;
    }

    public UUID provinceLeaderUserId() { return provinceLeaderUserId; }
    public String cityAccountManagerName() { return cityAccountManagerName; }
    public Instant updatedAt() { return updatedAt; }
    public Instant opportunityDate() { return opportunityDate; }

    /** 仅创建、导入和审批生效时记录项目日期，普通维护与进展不调用。 */
    public void recordOpportunityDate(Instant at) { opportunityDate = Objects.requireNonNull(at); }

    public void recordCategoryAmountChange(UUID actorId, Instant at) { touch(actorId, at); }

    public void rename(String newName, UUID actorId, Instant occurredAt) {
        projectName = newName;
        touch(actorId, occurredAt);
    }

    public void changeTotalAmount(BigDecimal newAmount, UUID actorId, Instant occurredAt) {
        totalAmount = newAmount;
        touch(actorId, occurredAt);
    }

    public boolean updateProfile(
            String customerName, String industryCode, String customerNeed,
            UUID provinceLeaderUserId, String cityAccountManagerName, List<String> expertNames,
            LocalDate bidMonth, List<String> scenarioCodes,
            ProjectNarrative narrative,
            UUID actorId, Instant occurredAt
    ) {
        if (Objects.equals(this.customerName, customerName)
                && Objects.equals(this.industryCode, industryCode)
                && Objects.equals(this.customerNeed, customerNeed)
                && Objects.equals(this.provinceLeaderUserId, provinceLeaderUserId)
                && Objects.equals(this.cityAccountManagerName, cityAccountManagerName)
                && Objects.equals(this.productSupportExpertNames, expertNames)
                && Objects.equals(this.plannedBidMonth, bidMonth)
                && Objects.equals(this.applicationScenarioCodes, scenarioCodes)
                && Objects.equals(this.constructionContentRaw, narrative.constructionContentRaw())
                && Objects.equals(this.productPromotionContent, narrative.productPromotionContent())
                && Objects.equals(this.remark, narrative.remark())) {
            return false;
        }
        this.customerName = customerName;
        this.industryCode = industryCode;
        this.customerNeed = customerNeed;
        this.provinceLeaderUserId = provinceLeaderUserId;
        this.cityAccountManagerName = cityAccountManagerName;
        replaceProductSupportExperts(expertNames);
        replacePlanningAttributes(bidMonth, scenarioCodes);
        replaceNarrative(narrative);
        touch(actorId, occurredAt);
        return true;
    }

    public void changeStage(ProjectStage stage, UUID actorId, Instant occurredAt) {
        currentStageCode = stage.name();
        if (stage.clearsContractFieldsOnEntry()) {
            contractNo = null;
            signedAmount = null;
        }
        touch(actorId, occurredAt);
    }

    public void advanceStage(
            ProjectStage target, String contractNo, BigDecimal signedAmount, UUID actorId, Instant approvedAt
    ) {
        if (target != ProjectStage.valueOf(currentStageCode).nextStage()) {
            throw new IllegalStateException("项目阶段不是相邻的正向推进");
        }
        currentStageCode = target.name();
        if (target == ProjectStage.COLLECTION) {
            this.contractNo = contractNo;
            this.signedAmount = signedAmount;
        }
        touch(actorId, approvedAt);
    }

    private void touch(UUID actorId, Instant occurredAt) {
        updatedBy = actorId;
        updatedAt = occurredAt;
    }
}
