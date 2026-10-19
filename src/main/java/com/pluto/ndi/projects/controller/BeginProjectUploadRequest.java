package com.pluto.ndi.projects.controller;

import com.pluto.ndi.documents.service.UploadCommand;
import com.pluto.ndi.projects.domain.ProjectDocumentPurpose;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 创建项目资料上传凭证的输入。 */
public record BeginProjectUploadRequest(
        @NotNull(message = "资料用途不得为空") ProjectDocumentPurpose purpose,
        @NotBlank(message = "文件名称不得为空") @Size(max = 255) String originalName,
        @NotBlank(message = "媒体类型不得为空") String mediaType,
        @Min(value = 1, message = "文件不得为空")
        @Max(value = 20971520, message = "文件不得超过20MB") long byteSize,
        @NotBlank(message = "SHA-256 不得为空")
        @Pattern(regexp = "[0-9a-fA-F]{64}", message = "SHA-256 格式不正确") String sha256Hex
) {
    UploadCommand toCommand() {
        return new UploadCommand(purpose.name(), originalName, mediaType, byteSize, sha256Hex);
    }
}
