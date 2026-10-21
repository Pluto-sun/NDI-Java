package com.pluto.ndi.backup.domain;

/** 归档失败只携带稳定业务原因，外部任务接口不返回驱动或存储凭据。 */
public class BackupFailure extends RuntimeException {
    public enum Reason { BUSY, UNSUPPORTED_SCHEMA, LIMIT_EXCEEDED, INTEGRITY_FAILED, STORAGE_FAILED }
    private final Reason reason;
    public BackupFailure(Reason reason) { super(reason.name()); this.reason = reason; }
    public BackupFailure(Reason reason, Throwable cause) { super(reason.name(), cause); this.reason = reason; }
    public Reason reason() { return reason; }
}
