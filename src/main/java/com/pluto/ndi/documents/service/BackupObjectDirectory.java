package com.pluto.ndi.documents.service;

import java.io.InputStream;
import java.util.UUID;

/** 备份仅流式读取已有正式对象，不生成公开链接；调用方负责关闭输入流。 */
public interface BackupObjectDirectory {
    InputStream openBackupObject(String bucket, String objectKey);
    ObjectLocation projectExportLocation(UUID exportId);

    record ObjectLocation(String bucket, String objectKey) { }
}
