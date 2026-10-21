package com.pluto.ndi.projects.controller;

import java.time.LocalDate;
import java.util.UUID;

import com.pluto.ndi.projects.service.AppendProgressCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 在线追加项目进展的输入。 */
public record AppendProjectProgressRequest(
        @NotBlank(message = "进展内容不得为空")
        @Size(max = 10000, message = "进展内容不得超过10000字") String progressContent,
        @NotBlank(message = "下一步动作不得为空")
        @Size(max = 10000, message = "下一步动作不得超过10000字") String nextAction,
        UUID correctsProgressId,
        LocalDate progressDate,
        @Size(max = 100, message = "进展负责人不得超过100字") String ownerName,
        LocalDate dueDate
) {
    AppendProgressCommand toCommand() {
        return new AppendProgressCommand(progressContent, nextAction,
                correctsProgressId, progressDate, ownerName, dueDate);
    }
}
