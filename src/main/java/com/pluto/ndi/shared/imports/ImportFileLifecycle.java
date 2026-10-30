package com.pluto.ndi.shared.imports;

import org.springframework.transaction.support.*;

import java.util.function.Consumer;

/** 新文件随回滚清理，旧文件随提交清理，避免业务回滚后丢失可恢复任务。 */
public final class ImportFileLifecycle {
    private ImportFileLifecycle() {}

    public static void release(
            ImportSourceStore files,
            String bucket,
            String key,
            boolean afterCommit,
            Consumer<RuntimeException> onFailure) {
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCompletion(int status) {
                        if ((status == STATUS_COMMITTED) == afterCommit)
                            try {
                                files.delete(bucket, key);
                            } catch (RuntimeException failure) {
                                onFailure.accept(failure);
                            }
                    }
                });
    }
}
