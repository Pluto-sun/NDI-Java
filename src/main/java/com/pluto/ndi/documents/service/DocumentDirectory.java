package com.pluto.ndi.documents.service;

import java.util.UUID;
import java.util.List;

/** 业务模块使用的文档上传、校验与下载能力。 */
public interface DocumentDirectory {

    UploadIntent beginUpload(String scopeType, UUID scopeId, UUID actorId, UploadCommand command);

    DocumentSummary completeUpload(String scopeType, UUID scopeId, UUID actorId, UUID documentId);

    boolean isUsable(String scopeType, UUID scopeId, UUID documentId, String purposeCode);

    DownloadIntent download(String scopeType, UUID scopeId, UUID documentId);

    List<DocumentSummary> listAvailable(String scopeType, UUID scopeId);
}
