package com.pluto.ndi.projects.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pluto.ndi.documents.service.ExportFileDirectory;
import com.pluto.ndi.identity.service.IdentityDirectory;
import com.pluto.ndi.identity.service.ProjectActor;
import com.pluto.ndi.identity.service.ProjectDataScope;
import com.pluto.ndi.projects.domain.ProjectErrorCode;
import com.pluto.ndi.projects.repository.ProjectExportDownloadRepository;
import com.pluto.ndi.projects.repository.ProjectExportTaskRepository;
import com.pluto.ndi.shared.web.BusinessException;
import org.junit.jupiter.api.Test;

class ProjectExportServiceTest {

    @Test
    void rejectsMoreThanOneThousandRowsBeforeTaskCreation() {
        var actorId = UUID.randomUUID();
        var provinceId = UUID.randomUUID();
        var cityId = UUID.randomUUID();
        var scope = new ProjectDataScope(ProjectDataScope.Level.CITY, cityId);
        var query = ProjectQuery.all();
        var projectService = mock(ProjectService.class);
        var identity = mock(IdentityDirectory.class);
        var tasks = mock(ProjectExportTaskRepository.class);
        when(identity.findProjectActor(actorId)).thenReturn(Optional.of(
                new ProjectActor(actorId, provinceId, cityId, true)));
        when(identity.findReadableProjectScope(new ProjectActor(actorId, provinceId, cityId, true)))
                .thenReturn(Optional.of(scope));
        when(projectService.countProjects(actorId, query)).thenReturn(1001L);
        var service = new ProjectExportService(projectService, identity, tasks,
                mock(ProjectExportDownloadRepository.class), mock(ExportFileDirectory.class),
                new ObjectMapper(), Clock.fixed(Instant.parse("2026-10-20T00:00:00Z"), ZoneOffset.UTC));

        var failure = catchThrowable(() -> service.submit(actorId, query));

        assertThat(((BusinessException) failure).errorCode()).isEqualTo(ProjectErrorCode.EXPORT_TOO_LARGE);
        verify(tasks, never()).save(org.mockito.ArgumentMatchers.any());
    }
}
