package com.pluto.ndi.projects.service;

import java.util.UUID;

/** 在线新增进展只接受本次正文、下一步动作与可选更正目标。 */
public record AppendProgressCommand(
        String progressContent,
        String nextAction,
        UUID correctsProgressId
) {
}
