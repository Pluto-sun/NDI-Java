package com.pluto.ndi.projects.util;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

import org.springframework.stereotype.Component;

/** 生成首期稳定且可读的项目编号，后续编号规则调整不影响内部项目标识。 */
@Component
public class ProjectNumberGenerator {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.BASIC_ISO_DATE;

    private final Clock clock;

    public ProjectNumberGenerator(Clock clock) {
        this.clock = clock;
    }

    public String nextCode() {
        var date = LocalDate.now(clock.withZone(BUSINESS_ZONE));
        var suffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        return "NDI-" + DATE_FORMAT.format(date) + "-" + suffix;
    }
}
