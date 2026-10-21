package com.pluto.ndi.projects.service;

import com.pluto.ndi.projects.domain.ProjectChangeField;

/** 名称、金额和阶段按字段提交，金额使用十进制字符串。 */
public record MajorChangeItem(ProjectChangeField fieldCode, String newValue) {
}
