package com.pluto.ndi.capabilities;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pluto.ndi.documents.service.*;
import com.pluto.ndi.shared.audit.AuditWriter;
import com.pluto.ndi.shared.web.*;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.Clock;

@Configuration
class IntelligenceImports {
    @Bean
    IntelligenceImport wangwuImport(
            IntelligenceService service,
            NamedParameterJdbcTemplate jdbc,
            ImportWorkbookDirectory files,
            ObjectMapper json,
            Clock clock,
            AuditWriter audit) {
        return new IntelligenceImport(
                service, IntelligenceKind.WANGWU, jdbc, files, json, clock, audit);
    }

    @Bean
    IntelligenceImport liveAgentImport(
            IntelligenceService service,
            NamedParameterJdbcTemplate jdbc,
            ImportWorkbookDirectory files,
            ObjectMapper json,
            Clock clock,
            AuditWriter audit) {
        return new IntelligenceImport(
                service, IntelligenceKind.AGENTS_LIVE, jdbc, files, json, clock, audit);
    }

    @Bean
    IntelligenceImport developmentAgentImport(
            IntelligenceService service,
            NamedParameterJdbcTemplate jdbc,
            ImportWorkbookDirectory files,
            ObjectMapper json,
            Clock clock,
            AuditWriter audit) {
        return new IntelligenceImport(
                service, IntelligenceKind.AGENTS_DEVELOPMENT, jdbc, files, json, clock, audit);
    }
}
