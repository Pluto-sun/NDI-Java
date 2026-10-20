package com.pluto.ndi.projects.service;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 启动时建立起始状态，并在月切及故障恢复后补齐缺失月份。 */
@Component
public class ProjectMonthlySnapshotJob implements ApplicationRunner {

    private final ProjectMonthlySnapshotService snapshots;

    public ProjectMonthlySnapshotJob(ProjectMonthlySnapshotService snapshots) {
        this.snapshots = snapshots;
    }

    @Override
    public void run(ApplicationArguments args) {
        snapshots.initialize();
        snapshots.generateDue();
    }

    @Scheduled(cron = "0 0 1 * * *", zone = "Asia/Shanghai")
    public void generateDue() {
        snapshots.generateDue();
    }
}
