package com.pluto.ndi.projects.service;

import java.util.List;
import java.util.UUID;

/** 项目普通信息的完整替换命令，不包含需审批的字段。 */
public record UpdateProjectProfileCommand(
        long expectedVersion,
        String customerName,
        String industryCode,
        String customerNeed,
        UUID provinceLeaderUserId,
        String cityAccountManagerName,
        List<String> productSupportExpertNames
) {
    public UpdateProjectProfileCommand(long expectedVersion, String customerName, String industryCode,
            String customerNeed, UUID provinceLeaderUserId, String cityAccountManagerName) {
        this(expectedVersion, customerName, industryCode, customerNeed, provinceLeaderUserId,
                cityAccountManagerName, null);
    }
}
