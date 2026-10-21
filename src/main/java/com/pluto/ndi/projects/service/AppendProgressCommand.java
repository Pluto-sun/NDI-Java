package com.pluto.ndi.projects.service;

import java.time.LocalDate;
import java.util.UUID;

/** 在线新增进展的业务字段与可选更正目标。 */
public record AppendProgressCommand(
        String progressContent,
        String nextAction,
        UUID correctsProgressId,
        LocalDate progressDate,
        String ownerName,
        LocalDate dueDate
) {
    public AppendProgressCommand(String progressContent, String nextAction, UUID correctsProgressId) {
        this(progressContent, nextAction, correctsProgressId, null, null, null);
    }
}
