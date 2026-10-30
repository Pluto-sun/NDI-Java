package com.pluto.ndi.shared.imports;

import java.util.UUID;

/** 公共导入仅依赖私有源文件端口，由文档模块提供对象存储实现。 */
public interface ImportSourceStore {
    record Stored(String bucket, String objectKey) {}

    Stored storeFor(String module, UUID id, byte[] bytes);

    byte[] read(String bucket, String objectKey);

    void delete(String bucket, String objectKey);
}
