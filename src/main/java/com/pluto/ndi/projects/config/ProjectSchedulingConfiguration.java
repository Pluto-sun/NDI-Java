package com.pluto.ndi.projects.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** 启用项目模块的审批提醒和月末快照调度。 */
@Configuration
@EnableScheduling
class ProjectSchedulingConfiguration {
}
