package com.pluto.ndi.projects.repository;

import java.util.UUID;
import java.util.List;
import java.time.Instant;

import com.pluto.ndi.projects.domain.entity.ProjectProgressEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProjectProgressRepository extends JpaRepository<ProjectProgressEntity, UUID> {

    Page<ProjectProgressEntity> findByProjectIdOrderByOccurredAtDescIdDesc(UUID projectId, Pageable pageable);

    @Query("""
            select new com.pluto.ndi.projects.repository.ProjectActivitySourceRow(
                progress.id, project.id, project.projectCode, project.projectName,
                province.provinceName, city.cityName, operator.displayName,
                progress.occurredAt,
                case when progress.sourceType <> 'CORRECTED' then 'PROGRESS_APPENDED'
                     else 'PROGRESS_CORRECTED' end, null)
            from ProjectProgressEntity progress
            join ProjectEntity project on project.id = progress.projectId
            join ProvinceEntity province on province.id = project.provinceId
            join CityEntity city on city.id = project.cityId
            join UserAccountEntity operator on operator.id = progress.actorId
            where progress.occurredAt >= :since
              and progress.sourceType <> 'INITIAL'
              and (:scopeLevel = 'NATIONAL'
                   or (:scopeLevel = 'PROVINCE' and project.provinceId = :scopeId)
                   or (:scopeLevel = 'CITY' and project.cityId = :scopeId))
            order by progress.occurredAt desc, progress.id desc
            """)
    List<ProjectActivitySourceRow> findRecentActivities(
            @Param("since") Instant since, @Param("scopeLevel") String scopeLevel,
            @Param("scopeId") UUID scopeId, Pageable pageable);
}
