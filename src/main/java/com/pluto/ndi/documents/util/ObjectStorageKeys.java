package com.pluto.ndi.documents.util;

import java.util.UUID;

/** 生成固定对象键，备份与导出读取沿用同一规则。 */
public final class ObjectStorageKeys {
    private ObjectStorageKeys() { }
    public static String projectExport(UUID id) { return "exports/projects/" + id + ".csv"; }
}
