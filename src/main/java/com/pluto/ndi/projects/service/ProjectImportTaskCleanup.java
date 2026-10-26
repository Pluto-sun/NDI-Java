package com.pluto.ndi.projects.service;

import com.pluto.ndi.documents.service.ImportWorkbookDirectory;
import com.pluto.ndi.projects.domain.ProjectImportTask;
import com.pluto.ndi.projects.mapper.ProjectImportTaskMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.*;

import java.util.UUID;

@Service
public class ProjectImportTaskCleanup {
    private static final Logger LOG = LoggerFactory.getLogger(ProjectImportTaskCleanup.class);
    private final ProjectImportTaskMapper tasks;
    private final ImportWorkbookDirectory files;
    private final TransactionTemplate transactions;

    public ProjectImportTaskCleanup(
            ProjectImportTaskMapper tasks,
            ImportWorkbookDirectory files,
            TransactionTemplate transactions) {
        this.tasks = tasks;
        this.files = files;
        this.transactions = transactions;
    }

    public void completed(UUID batch) {
        transactions.executeWithoutResult(
                status ->
                        tasks.findByBatch(batch)
                                .ifPresent(
                                        task -> {
                                            tasks.lock(task.actorId());
                                            // 再核对，不能误删该账号刚开启的下一项任务。
                                            tasks.findLocked(task.actorId())
                                                    .filter(
                                                            current ->
                                                                    batch.equals(current.batchId()))
                                                    .ifPresent(
                                                            current -> {
                                                                tasks.delete(current.actorId());
                                                                release(current, true);
                                                            });
                                        }));
    }

    public void release(ProjectImportTask task, boolean afterCommit) {
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCompletion(int status) {
                        if ((status == STATUS_COMMITTED) == afterCommit) {
                            try {
                                files.delete(task.bucket(), task.objectKey());
                            } catch (RuntimeException failure) {
                                LOG.error("未完成导入源文件清理失败，任务编号：{}", task.id(), failure);
                            }
                        }
                    }
                });
    }
}
