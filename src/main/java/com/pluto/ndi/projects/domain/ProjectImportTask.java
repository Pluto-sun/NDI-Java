package com.pluto.ndi.projects.domain;

import com.pluto.ndi.projects.util.ProjectExcelReader;

import java.time.Instant;
import java.util.*;

/** 向导游标与源文件引用，已完成批次不占用账号的未完成任务。 */
public record ProjectImportTask(
        UUID id,
        UUID actorId,
        long version,
        int step,
        String fileName,
        String bucket,
        String objectKey,
        long byteSize,
        String sha256,
        ProjectExcelReader.WorkbookInspection inspection,
        Configuration configuration,
        List<Integer> selectedRows,
        Configuration previewConfiguration,
        UUID batchId,
        Instant updatedAt) {
    public record Configuration(
            String sheetName, Integer headerRowNo, Map<String, String> fieldMapping) {}
}
