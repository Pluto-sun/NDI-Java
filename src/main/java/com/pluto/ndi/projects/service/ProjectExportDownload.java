package com.pluto.ndi.projects.service;

/** 经授权签发的短时下载链接。 */
public record ProjectExportDownload(String url, String fileName) {
}
