package com.pluto.ndi.backup.config;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.Min;

/** 归档目录应挂载独立受限存储，不得通过静态资源或对象公开桶暴露。 */
@Validated
@ConfigurationProperties("ndi.backup")
public record BackupProperties(
        @DefaultValue("./var/backups") Path directory,
        @DefaultValue("2147483648") @Min(1048576) long maxUncompressedBytes,
        @DefaultValue("10000") @Min(1) int maxObjects,
        @DefaultValue("600") @Min(1) int timeoutSeconds) { }
