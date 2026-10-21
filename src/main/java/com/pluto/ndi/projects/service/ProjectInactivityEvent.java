package com.pluto.ndi.projects.service;

import java.time.Instant;

/** 督办链中的系统升级、人工转发或闭环事件。 */
public record ProjectInactivityEvent(String kind, String summary, Instant occurredAt) {
}
