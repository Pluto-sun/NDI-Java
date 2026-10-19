package com.pluto.ndi.projects.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** 启用项目审批逾期提醒的周期扫描。 */
@Configuration
@EnableScheduling
class ProjectApprovalSchedulingConfiguration {
}
