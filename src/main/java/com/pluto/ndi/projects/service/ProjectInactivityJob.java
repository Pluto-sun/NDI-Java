package com.pluto.ndi.projects.service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import com.pluto.ndi.projects.mapper.ProjectInactivityMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 每日按活动时间游标扫描，单个项目失败不阻断整批。 */
@Component
@ConditionalOnProperty(name = "ndi.projects.inactivity.enabled", havingValue = "true", matchIfMissing = true)
public class ProjectInactivityJob {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProjectInactivityJob.class);
    private static final int BATCH_SIZE = 200;
    private final ProjectInactivityMapper mapper;
    private final ProjectInactivityService service;
    private final Clock clock;

    public ProjectInactivityJob(ProjectInactivityMapper mapper, ProjectInactivityService service, Clock clock) {
        this.mapper = mapper;
        this.service = service;
        this.clock = clock;
    }

    @Scheduled(cron = "${ndi.projects.inactivity.cron:0 0 9 * * *}", zone = "Asia/Shanghai")
    public void scan() {
        var now = Instant.now(clock);
        var cursorAt = Instant.EPOCH;
        var cursorId = new UUID(0, 0);
        int size;
        do {
            var batch = mapper.candidates(now.minus(30, ChronoUnit.DAYS), cursorAt, cursorId, BATCH_SIZE);
            for (var candidate : batch) {
                try {
                    service.recordIfDue(candidate.id(), now).filter(result -> "NO_RECIPIENTS".equals(result.status()))
                            .ifPresent(result -> LOGGER.warn("项目督办缺少启用接收人，项目 ID={}，层级={}",
                                    candidate.id(), result.audience()));
                } catch (RuntimeException exception) {
                    LOGGER.error("项目超时督办扫描失败，项目 ID={}", candidate.id(), exception);
                }
            }
            size = batch.size();
            if (size > 0) {
                var last = batch.getLast();
                cursorAt = last.lastActivityAt();
                cursorId = last.id();
            }
        } while (size == BATCH_SIZE);
    }
}
