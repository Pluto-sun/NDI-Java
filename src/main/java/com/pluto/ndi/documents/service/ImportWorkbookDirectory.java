package com.pluto.ndi.documents.service;

import java.util.UUID;

/** 未完成导入的私有源文件；仅服务端读取，不生成公开链接。 */
public interface ImportWorkbookDirectory extends com.pluto.ndi.shared.imports.ImportSourceStore {

    Stored store(UUID id, byte[] bytes);

    Stored storeFor(String module, UUID id, byte[] bytes);

    byte[] read(String bucket, String objectKey);

    void delete(String bucket, String objectKey);
}
