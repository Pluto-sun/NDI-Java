package com.pluto.ndi.projects.service;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import com.pluto.ndi.projects.domain.ApprovalStatus;
import com.pluto.ndi.projects.repository.ProjectApprovalRequestRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 每日扫描已到期的审批；提醒不改变审批状态。 */
@Component
@ConditionalOnProperty(name = "ndi.approvals.reminders-enabled", havingValue = "true", matchIfMissing = true)
public class ProjectApprovalReminderJob {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProjectApprovalReminderJob.class);
    private static final int BATCH_SIZE = 200;

    private final ProjectApprovalRequestRepository approvalRepository;
    private final ProjectApprovalReminderWriter writer;
    private final Clock clock;

    public ProjectApprovalReminderJob(
            ProjectApprovalRequestRepository approvalRepository, ProjectApprovalReminderWriter writer, Clock clock
    ) {
        this.approvalRepository = approvalRepository;
        this.writer = writer;
        this.clock = clock;
    }

    @Scheduled(
            fixedDelayString = "${ndi.approvals.reminder-scan-delay-ms:86400000}",
            initialDelayString = "${ndi.approvals.reminder-initial-delay-ms:60000}"
    )
    public void scan() {
        var now = Instant.now(clock);
        var cursorDueAt = Instant.EPOCH;
        var cursorId = new UUID(0, 0);
        int batchSize;
        do {
            var batch = approvalRepository.findOverdueAfter(
                    ApprovalStatus.PENDING, now, cursorDueAt, cursorId, PageRequest.of(0, BATCH_SIZE));
            for (var request : batch) {
                try {
                    writer.recordIfDue(request.id(), now);
                } catch (RuntimeException exception) {
                    LOGGER.error("审批逾期提醒写入失败，申请 ID={}", request.id(), exception);
                }
            }
            batchSize = batch.size();
            if (batchSize > 0) {
                var last = batch.getLast();
                cursorDueAt = last.dueAt();
                cursorId = last.id();
            }
        } while (batchSize == BATCH_SIZE);
    }
}
