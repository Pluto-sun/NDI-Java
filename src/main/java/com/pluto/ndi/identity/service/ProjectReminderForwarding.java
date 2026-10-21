package com.pluto.ndi.identity.service;

import java.util.List;

/** 身份模块返回的督办转发资格与候选接收人。 */
public record ProjectReminderForwarding(String route, List<ProjectUser> recipients) {
    public ProjectReminderForwarding {
        recipients = List.copyOf(recipients);
    }
}
