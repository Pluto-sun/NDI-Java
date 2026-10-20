package com.pluto.ndi.projects.domain;

/** 项目生命周期的稳定阶段代码。 */
public enum ProjectStage {
    SOLUTION_EXCHANGE(0, "方案交流"),
    BIDDING(1, "招投标"),
    SIGNED(2, "签约"),
    COLLECTION(3, "计收"),
    TERMINATED(-1, "已终止");

    private final int forwardOrder;
    private final String label;

    ProjectStage(int forwardOrder, String label) {
        this.forwardOrder = forwardOrder;
        this.label = label;
    }

    public String label() {
        return label;
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
