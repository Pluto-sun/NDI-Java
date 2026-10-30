package com.pluto.ndi.shared.approval;

import java.util.function.Supplier;

/** 通用审批守卫；锁、权限及生效动作仍由各模块在业务事务内执行。 */
public final class ApprovalWorkflow {
    private ApprovalWorkflow() {}

    public static void requirePending(String status, Supplier<? extends RuntimeException> failure) {
        if (!"PENDING".equals(status)) throw failure.get();
    }

    public static void requireNoPending(
            boolean pending, Supplier<? extends RuntimeException> failure) {
        if (pending) throw failure.get();
    }

    public static void requireOpinion(
            boolean approved, String opinion, Supplier<? extends RuntimeException> failure) {
        if (!approved && (opinion == null || opinion.isBlank())) throw failure.get();
    }

    public static boolean sameVersion(long actual, long expected) {
        return actual == expected;
    }
}
