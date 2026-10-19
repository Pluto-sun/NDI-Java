package com.pluto.ndi.documents.service;

/** 上传申请的文件元数据，由业务模块确认归属范围后传入。 */
public record UploadCommand(
        String purposeCode,
        String originalName,
        String mediaType,
        long byteSize,
        String sha256Hex
) {
}
