package com.pluto.ndi.projects.service;

import java.util.List;
import java.util.UUID;

/** 同地市项目名称的精确重复与相似候选。 */
public record ProjectDuplicateCheck(String status, List<Candidate> candidates) {
    public ProjectDuplicateCheck {
        candidates = List.copyOf(candidates);
    }

    public record Candidate(UUID projectId, String projectCode, String projectName, int similarity) { }
}
