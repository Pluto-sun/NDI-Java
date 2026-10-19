package com.pluto.ndi.projects.service;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import com.pluto.ndi.projects.domain.ApprovalStatus;
import com.pluto.ndi.projects.repository.ProjectApprovalNotificationRepository;
import com.pluto.ndi.projects.repository.ProjectApprovalRequestRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 逐单检查待审状态，以唯一事件键抵抗多实例重复扫描。 */
@Service
public class ProjectApprovalReminderWriter {

    private static final long REMINDER_INTERVAL_SECONDS = 24 * 60 * 60;

    private final ProjectApprovalRequestRepository approvalRepository;
    private final ProjectApprovalNotificationRepository notificationRepository;

    public ProjectApprovalReminderWriter(
            ProjectApprovalRequestRepository approvalRepository,
            ProjectApprovalNotificationRepository notificationRepository
    ) {
        this.approvalRepository = approvalRepository;
        this.notificationRepository = notificationRepository;
    }

    @Transactional
    public boolean recordIfDue(UUID requestId, Instant now) {
        var request = approvalRepository.findLockedById(requestId).orElse(null);
        if (request == null || request.status() != ApprovalStatus.PENDING || request.dueAt().isAfter(now)) {
            return false;
        }
        long sequence = Duration.between(request.dueAt(), now).getSeconds() / REMINDER_INTERVAL_SECONDS + 1;
        String eventKey = request.id() + ":OVERDUE:" + sequence;
        notificationRepository.insertOverdueIfAbsent(
                UUID.randomUUID().toString(), request.id().toString(), request.approverId().toString(), eventKey, now
        );
        return true;
    }
}
