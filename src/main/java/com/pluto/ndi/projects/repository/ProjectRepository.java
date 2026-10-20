package com.pluto.ndi.projects.repository;

import java.util.List;
import java.util.UUID;

import com.pluto.ndi.projects.domain.entity.ProjectEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.Optional;

public interface ProjectRepository extends JpaRepository<ProjectEntity, UUID>, JpaSpecificationExecutor<ProjectEntity> {

    List<ProjectEntity> findByCityId(UUID cityId);

    @Query("""
            select new com.pluto.ndi.projects.repository.ProjectNameCandidate(
                project.id, project.projectCode, project.projectName)
            from ProjectEntity project where project.cityId = :cityId
            """)
    List<ProjectNameCandidate> findNamesByCityId(@Param("cityId") UUID cityId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select project from ProjectEntity project where project.id = :id")
    Optional<ProjectEntity> findLockedById(UUID id);

    boolean existsByContractNoAndIdNot(String contractNo, UUID id);

    @Query("""
            select project.currentStageCode as stageCode, count(project) as projectCount,
                   sum(project.totalAmount) as expectedAmount
            from ProjectEntity project
            where project.provinceId = :provinceId
              and (:includeTerminated = true or project.currentStageCode <> 'TERMINATED')
            group by project.currentStageCode
            """)
    List<ProjectStageAggregate> summarizeCurrentStagesByProvince(
            @Param("provinceId") UUID provinceId, @Param("includeTerminated") boolean includeTerminated);

    @Query("""
            select project.currentStageCode as stageCode, count(project) as projectCount,
                   sum(project.totalAmount) as expectedAmount
            from ProjectEntity project
            where project.cityId = :cityId
              and (:includeTerminated = true or project.currentStageCode <> 'TERMINATED')
            group by project.currentStageCode
            """)
    List<ProjectStageAggregate> summarizeCurrentStagesByCity(
            @Param("cityId") UUID cityId, @Param("includeTerminated") boolean includeTerminated);

    @Query("""
            select project.cityId as cityId, count(project) as projectCount,
                   sum(project.totalAmount) as expectedAmount
            from ProjectEntity project
            where project.provinceId = :provinceId
              and (:includeTerminated = true or project.currentStageCode <> 'TERMINATED')
            group by project.cityId
            """)
    List<ProjectCityAggregate> summarizeCurrentCitiesByProvince(
            @Param("provinceId") UUID provinceId, @Param("includeTerminated") boolean includeTerminated);

    @Query("""
            select project.cityId as cityId, count(project) as projectCount,
                   sum(project.totalAmount) as expectedAmount
            from ProjectEntity project
            where project.cityId = :cityId
              and (:includeTerminated = true or project.currentStageCode <> 'TERMINATED')
            group by project.cityId
            """)
    List<ProjectCityAggregate> summarizeCurrentCitiesByCity(
            @Param("cityId") UUID cityId, @Param("includeTerminated") boolean includeTerminated);

    @Query("""
            select project.industryCode as industryCode, count(project) as projectCount,
                   sum(project.totalAmount) as expectedAmount
            from ProjectEntity project
            where project.provinceId = :provinceId
              and (:includeTerminated = true or project.currentStageCode <> 'TERMINATED')
            group by project.industryCode
            """)
    List<ProjectIndustryAggregate> summarizeCurrentIndustriesByProvince(
            @Param("provinceId") UUID provinceId, @Param("includeTerminated") boolean includeTerminated);

    @Query("""
            select project.industryCode as industryCode, count(project) as projectCount,
                   sum(project.totalAmount) as expectedAmount
            from ProjectEntity project
            where project.cityId = :cityId
              and (:includeTerminated = true or project.currentStageCode <> 'TERMINATED')
            group by project.industryCode
            """)
    List<ProjectIndustryAggregate> summarizeCurrentIndustriesByCity(
            @Param("cityId") UUID cityId, @Param("includeTerminated") boolean includeTerminated);
}
