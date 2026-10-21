package com.pluto.ndi.projects.repository;

import java.util.UUID;
import java.util.List;
import java.time.Instant;

import com.pluto.ndi.projects.domain.entity.ProjectChangeEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProjectChangeEventRepository extends JpaRepository<ProjectChangeEventEntity, UUID> {
    Page<ProjectChangeEventEntity> findByProjectIdOrderByOccurredAtDescIdDesc(UUID projectId, Pageable pageable);

    @Query("""
            select new com.pluto.ndi.projects.repository.ProjectActivitySourceRow(
                event.id, project.id, project.projectCode, project.projectName,
                province.provinceName, city.cityName, operator.displayName,
                event.occurredAt, event.eventType, event.payload)
            from ProjectChangeEventEntity event
            join ProjectEntity project on project.id = event.projectId
            join ProvinceEntity province on province.id = project.provinceId
            join CityEntity city on city.id = project.cityId
            join UserAccountEntity operator on operator.id = event.actorId
            where event.occurredAt >= :since
              and (:scopeLevel = 'NATIONAL'
                   or (:scopeLevel = 'PROVINCE' and project.provinceId in :provinceIds)
                   or (:scopeLevel = 'CITY' and project.cityId = :scopeId))
            order by event.occurredAt desc, event.id desc
            """)
    List<ProjectActivitySourceRow> findRecentActivities(
            @Param("since") Instant since, @Param("scopeLevel") String scopeLevel,
            @Param("scopeId") UUID scopeId, @Param("provinceIds") java.util.Set<UUID> provinceIds, Pageable pageable);
}
