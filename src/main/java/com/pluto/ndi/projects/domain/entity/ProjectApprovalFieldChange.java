package com.pluto.ndi.projects.domain.entity;

import java.util.Objects;

import com.pluto.ndi.projects.domain.ProjectChangeField;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

/** 提交时冻结的单个字段前后值，审批处理时不再改写。 */
@Embeddable
public class ProjectApprovalFieldChange {
    @Enumerated(EnumType.STRING)
    @Column(name = "field_code", nullable = false, length = 40)
    private ProjectChangeField fieldCode;

    @Column(name = "original_value", length = 200)
    private String originalValue;

    @Column(name = "proposed_value", nullable = false, length = 200)
    private String proposedValue;

    protected ProjectApprovalFieldChange() { }

    public ProjectApprovalFieldChange(ProjectChangeField fieldCode, String originalValue, String proposedValue) {
        this.fieldCode = fieldCode;
        this.originalValue = originalValue;
        this.proposedValue = proposedValue;
    }

    public ProjectChangeField fieldCode() { return fieldCode; }
    public String originalValue() { return originalValue; }
    public String proposedValue() { return proposedValue; }

    @Override
    public boolean equals(Object value) {
        return value instanceof ProjectApprovalFieldChange other && fieldCode == other.fieldCode
                && Objects.equals(originalValue, other.originalValue) && Objects.equals(proposedValue, other.proposedValue);
    }

    @Override
    public int hashCode() { return Objects.hash(fieldCode, originalValue, proposedValue); }
}
