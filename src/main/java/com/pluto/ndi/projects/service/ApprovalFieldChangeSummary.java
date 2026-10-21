package com.pluto.ndi.projects.service;

import com.pluto.ndi.projects.domain.ProjectChangeField;

/** 审批页面显示冻结的原值与目标值。 */
public record ApprovalFieldChangeSummary(ProjectChangeField fieldCode, String originalValue, String proposedValue) {
}
