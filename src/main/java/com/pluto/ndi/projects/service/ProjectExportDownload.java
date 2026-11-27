package com.pluto.ndi.projects.service;

import org.springframework.core.io.InputStreamSource;

/** 授权通过后由 HTTP 层开启文件流，避免数据库事务持有 MinIO 连接。 */
public record ProjectExportDownload(InputStreamSource content, String fileName) {
}
