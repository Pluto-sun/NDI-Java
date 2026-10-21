package com.pluto.ndi.backup.domain;

/** 命令行仅输出稳定原因，不直接打印驱动异常或 SQL 内容。 */
public class RestoreFailure extends RuntimeException {
    public enum Reason { CONFIG_INVALID, ARCHIVE_INVALID, TARGET_NOT_EMPTY, TARGET_BUSY, TARGET_UNAVAILABLE, RESTORE_FAILED }
    private final Reason reason;
    public RestoreFailure(Reason reason) { super(reason.name()); this.reason = reason; }
    public RestoreFailure(Reason reason, Throwable cause) { super(reason.name(), cause); this.reason = reason; }
    public Reason reason() { return reason; }
}
