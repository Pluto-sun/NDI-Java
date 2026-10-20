package com.pluto.ndi.projects.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.pluto.ndi.identity.service.IdentityDirectory;
import com.pluto.ndi.identity.service.ProjectActor;
import com.pluto.ndi.organization.service.CityOption;
import com.pluto.ndi.organization.service.OrganizationDirectory;
import com.pluto.ndi.projects.domain.ProjectErrorCode;
import com.pluto.ndi.projects.repository.ProjectNameCandidate;
import com.pluto.ndi.projects.repository.ProjectRepository;
import com.pluto.ndi.shared.web.BusinessException;
import org.junit.jupiter.api.Test;

class ProjectDuplicateServiceTest {

    @Test
    void findsNormalizedExactNameInCity() {
        var cityId = UUID.randomUUID();
        var existing = UUID.randomUUID();
        var projects = mock(ProjectRepository.class);
        when(projects.findNamesByCityId(cityId)).thenReturn(List.of(
                new ProjectNameCandidate(existing, "NDI-001", "ＡＢＣ－智慧工厂（一期）项目")));
        var service = new ProjectDuplicateService(mock(IdentityDirectory.class),
                mock(OrganizationDirectory.class), projects);

        var result = service.candidates(cityId, "ABC 智慧工厂一期项目");

        assertThat(result.status()).isEqualTo("STRONG_DUPLICATE");
        assertThat(result.candidates()).extracting(ProjectDuplicateCheck.Candidate::projectId)
                .containsExactly(existing);
    }

    @Test
    void refusesToRevealCandidatesOutsideCreationScope() {
        var actorId = UUID.randomUUID();
        var provinceId = UUID.randomUUID();
        var cityId = UUID.randomUUID();
        var actor = new ProjectActor(actorId, provinceId, null, true);
        var identity = mock(IdentityDirectory.class);
        var organizations = mock(OrganizationDirectory.class);
        var projects = mock(ProjectRepository.class);
        when(identity.findProjectActor(actorId)).thenReturn(Optional.of(actor));
        when(organizations.findEnabledCities()).thenReturn(List.of(new CityOption(cityId, provinceId, "演示地市")));
        var service = new ProjectDuplicateService(identity, organizations, projects);

        var failure = catchThrowable(() -> service.check(actorId, cityId, "某项目"));

        assertThat(((BusinessException) failure).errorCode()).isEqualTo(ProjectErrorCode.CREATION_FORBIDDEN);
        verify(projects, never()).findNamesByCityId(cityId);
    }
}
