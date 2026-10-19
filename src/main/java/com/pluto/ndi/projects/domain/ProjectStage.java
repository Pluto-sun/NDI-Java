package com.pluto.ndi.projects.domain;

/** 项目生命周期的稳定阶段代码。 */
public enum ProjectStage {
    SOLUTION_EXCHANGE(0),
    BIDDING(1),
    SIGNED(2),
    COLLECTION(3),
    TERMINATED(-1);

    private final int forwardOrder;

    ProjectStage(int forwardOrder) {
        this.forwardOrder = forwardOrder;
    }

    public boolean isBefore(ProjectStage other) {
        return forwardOrder >= 0 && other.forwardOrder >= 0 && forwardOrder < other.forwardOrder;
    }

    public boolean clearsContractFieldsOnEntry() {
        return this == SOLUTION_EXCHANGE || this == BIDDING;
    }

    public ProjectStage nextStage() {
        return switch (this) {
            case SOLUTION_EXCHANGE -> BIDDING;
            case BIDDING -> SIGNED;
            case SIGNED -> COLLECTION;
            case COLLECTION, TERMINATED -> null;
        };
    }
}
