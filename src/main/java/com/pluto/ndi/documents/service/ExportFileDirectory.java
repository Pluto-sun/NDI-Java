package com.pluto.ndi.documents.service;

import java.util.UUID;

/** 向业务模块提供服务端生成文件的对象存储接口。 */
public interface ExportFileDirectory {

    void storeCsv(UUID exportId, byte[] content);

    String signCsvDownload(UUID exportId);
}
