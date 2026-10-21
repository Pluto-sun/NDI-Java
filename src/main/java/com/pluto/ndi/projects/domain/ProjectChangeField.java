package com.pluto.ndi.projects.domain;

/** 重大变更允许修改的字段；正向阶段推进仍使用独立申请。 */
public enum ProjectChangeField {
    PROJECT_NAME, TOTAL_AMOUNT, NETWORK_AMOUNT, GEWU_AMOUNT, BIG_DATA_AMOUNT, WANGWU_AMOUNT, STAGE;

    public String categoryCode() {
        return switch (this) {
            case NETWORK_AMOUNT -> "NETWORK";
            case GEWU_AMOUNT -> "GEWU";
            case BIG_DATA_AMOUNT -> "BIG_DATA";
            case WANGWU_AMOUNT -> "WANGWU";
            default -> null;
        };
    }
}
