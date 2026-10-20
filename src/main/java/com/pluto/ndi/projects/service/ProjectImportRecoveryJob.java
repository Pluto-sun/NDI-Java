package com.pluto.ndi.projects.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 周期性恢复中断超过十五分钟的导入确认批次。 */
@Component
public class ProjectImportRecoveryJob {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProjectImportRecoveryJob.class);
    private final ProjectImportService imports;

    public ProjectImportRecoveryJob(ProjectImportService imports) { this.imports = imports; }

    @Scheduled(fixedDelayString = "${ndi.projects.import.recovery-delay-ms:300000}",
            initialDelayString = "${ndi.projects.import.recovery-initial-delay-ms:300000}")
    public void recover() {
        try {
            imports.recoverStale();
        } catch (RuntimeException exception) {
            LOGGER.error("导入批次恢复扫描失败；下一轮将继续尝试", exception);
        }
    }
}
