package com.pluto.ndi.projects.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

import com.pluto.ndi.projects.domain.ProjectChangeField;
import com.pluto.ndi.projects.domain.ProjectErrorCode;
import com.pluto.ndi.projects.domain.ProjectStage;
import com.pluto.ndi.projects.domain.entity.ProjectApprovalFieldChange;
import com.pluto.ndi.projects.domain.entity.ProjectApprovalRequestEntity;
import com.pluto.ndi.projects.domain.entity.ProjectCategoryAmountEntity;
import com.pluto.ndi.projects.domain.entity.ProjectEntity;
import com.pluto.ndi.projects.repository.ProjectCategoryAmountRepository;
import com.pluto.ndi.projects.util.ProjectNameMatcher;
import com.pluto.ndi.shared.web.BusinessException;
import org.springframework.stereotype.Component;

/** 组合变更先计算完整目标金额，统一校验后冻结字段差异。 */
@Component
public class ProjectMajorChangeFields {
    private static final Pattern MONEY = Pattern.compile("[0-9]{1,16}(?:\\.[0-9]{1,2})?");
    private final ProjectCategoryAmountRepository categories;

    public ProjectMajorChangeFields(ProjectCategoryAmountRepository categories) {
        this.categories = categories;
    }

    public List<ProjectApprovalFieldChange> validate(ProjectEntity project, List<MajorChangeItem> inputs) {
        if (inputs == null || inputs.isEmpty() || inputs.size() > ProjectChangeField.values().length) invalid();
        var originalAmounts = amounts(project.id());
        var targetAmounts = new LinkedHashMap<>(originalAmounts);
        var total = project.totalAmount();
        var seen = EnumSet.noneOf(ProjectChangeField.class);
        var changes = new ArrayList<ProjectApprovalFieldChange>();
        for (var input : inputs) {
            if (input == null || input.fieldCode() == null || !seen.add(input.fieldCode())
                    || input.newValue() == null || input.newValue().isBlank()) invalid();
            var field = input.fieldCode();
            var value = input.newValue().trim();
            if (value.length() > 200) invalid();
            if (field == ProjectChangeField.PROJECT_NAME) {
                if (ProjectNameMatcher.standard(value).isBlank()) invalid();
            } else if (field == ProjectChangeField.STAGE) {
                ProjectStage target;
                try { target = ProjectStage.valueOf(value); }
                catch (IllegalArgumentException exception) { throw new BusinessException(ProjectErrorCode.INVALID_MAJOR_CHANGE); }
                if (target != ProjectStage.TERMINATED && !target.isBefore(ProjectStage.valueOf(project.currentStageCode()))) invalid();
            } else {
                if (!MONEY.matcher(value).matches()) invalid();
                var amount = new BigDecimal(value).setScale(2);
                value = amount.toPlainString();
                if (field == ProjectChangeField.TOTAL_AMOUNT) total = amount;
                else targetAmounts.put(field.categoryCode(), amount);
            }
            var before = original(project, originalAmounts, field);
            if (!Objects.equals(before, value)) changes.add(new ProjectApprovalFieldChange(field, before, value));
        }
        if (changes.isEmpty()) invalid();
        var sum = targetAmounts.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        if (sum.compareTo(total) > 0) throw new BusinessException(ProjectErrorCode.INVALID_CATEGORY_AMOUNT);
        return List.copyOf(changes);
    }

    public boolean conflicts(ProjectEntity project, ProjectApprovalRequestEntity request) {
        var current = amounts(project.id());
        if (request.fieldChanges().stream().anyMatch(item -> !Objects.equals(item.originalValue(),
                original(project, current, item.fieldCode())))) return true;
        try {
            validate(project, request.fieldChanges().stream().map(item ->
                    new MajorChangeItem(item.fieldCode(), item.proposedValue())).toList());
            return false;
        } catch (BusinessException exception) {
            return true;
        }
    }

    public void apply(ProjectEntity project, ProjectApprovalRequestEntity request, UUID actorId, Instant at) {
        var current = new LinkedHashMap<String, ProjectCategoryAmountEntity>();
        categories.findByProjectIdIn(List.of(project.id())).forEach(item -> current.put(item.categoryCode(), item));
        for (var item : request.fieldChanges()) {
            var field = item.fieldCode();
            switch (field) {
                case PROJECT_NAME -> project.rename(item.proposedValue(), actorId, at);
                case TOTAL_AMOUNT -> project.changeTotalAmount(new BigDecimal(item.proposedValue()), actorId, at);
                case STAGE -> project.changeStage(ProjectStage.valueOf(item.proposedValue()), actorId, at);
                default -> {
                    var category = current.get(field.categoryCode());
                    var value = new BigDecimal(item.proposedValue());
                    if (category == null) categories.save(ProjectCategoryAmountEntity.create(project.id(), field.categoryCode(), value));
                    else category.changeExpectedAmount(value);
                    project.recordCategoryAmountChange(actorId, at);
                }
            }
        }
    }

    public Map<String, BigDecimal> amounts(UUID projectId) {
        var result = new LinkedHashMap<String, BigDecimal>();
        categories.findByProjectIdIn(List.of(projectId)).stream()
                .sorted(java.util.Comparator.comparing(ProjectCategoryAmountEntity::categoryCode))
                .forEach(item -> result.put(item.categoryCode(), item.expectedAmount()));
        return result;
    }

    private String original(ProjectEntity project, Map<String, BigDecimal> amounts, ProjectChangeField field) {
        return switch (field) {
            case PROJECT_NAME -> project.projectName();
            case TOTAL_AMOUNT -> project.totalAmount().setScale(2).toPlainString();
            case STAGE -> project.currentStageCode();
            default -> amounts.containsKey(field.categoryCode()) ? amounts.get(field.categoryCode()).setScale(2).toPlainString() : null;
        };
    }

    private void invalid() { throw new BusinessException(ProjectErrorCode.INVALID_MAJOR_CHANGE); }
}
