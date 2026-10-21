package com.pluto.ndi.backup.service;

import java.sql.SQLException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class BackupTaskWorkerTest {
    @Test
    @Timeout(5)
    void dispatchUsesDedicatedThreadAndDoesNotQueueAnotherRunWhileBusy() throws Exception {
        var source = mock(DataSource.class);
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var thread = new AtomicReference<String>();
        when(source.getConnection()).thenAnswer(invocation -> {
            thread.set(Thread.currentThread().getName()); entered.countDown();
            release.await();
            throw new SQLException("隔离测试主动结束工作连接");
        });
        var worker = new BackupTaskWorker(source,mock(BackupTaskService.class),mock(BackupArchiveService.class));
        try {
            worker.dispatch();
            assertThat(entered.await(2,TimeUnit.SECONDS)).isTrue();
            assertThat(thread.get()).isEqualTo("ndi-backup-worker");
            worker.dispatch();
            verify(source,times(1)).getConnection();
        } finally { release.countDown(); worker.stop(); }
    }
}
