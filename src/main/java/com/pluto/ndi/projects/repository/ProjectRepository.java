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
    @Query("select new com.pluto.ndi.projects.domain.ProjectExportDetails(p.id,p.provinceId,p.cityId,p.provinceLeaderUserId,p.signingSubjectCode,p.cityAccountManagerName,p.updatedAt) from ProjectEntity p where p.id in :ids")
    List<com.pluto.ndi.projects.domain.ProjectExportDetails> findExportDetails(@Param("ids") java.util.Collection<UUID> ids);

    @Query("select new com.pluto.ndi.projects.domain.ProjectExportExpert(p.id,e) from ProjectEntity p join p.productSupportExpertNames e where p.id in :ids order by p.id,index(e)")
    List<com.pluto.ndi.projects.domain.ProjectExportExpert> findExportExperts(@Param("ids") java.util.Collection<UUID> ids);

    @Query("select project.currentStageCode as stageCode, count(project) as projectCount, sum(project.totalAmount) as expectedAmount "
            + "from ProjectEntity project where project.provinceId in :provinceIds "
            + "and (:includeTerminated = true or project.currentStageCode <> 'TERMINATED') group by project.currentStageCode")
    List<ProjectStageAggregate> summarizeCurrentStagesByProvinces(java.util.Set<UUID> provinceIds, boolean includeTerminated);

    @Query("select project.cityId as cityId, count(project) as projectCount, sum(project.totalAmount) as expectedAmount "
            + "from ProjectEntity project where project.provinceId in :provinceIds "
            + "and (:includeTerminated = true or project.currentStageCode <> 'TERMINATED') group by project.cityId")
    List<ProjectCityAggregate> summarizeCurrentCitiesByProvinces(java.util.Set<UUID> provinceIds, boolean includeTerminated);

    @Query("select project.industryCode as industryCode, count(project) as projectCount, sum(project.totalAmount) as expectedAmount "
            + "from ProjectEntity project where project.provinceId in :provinceIds "
            + "and (:includeTerminated = true or project.currentStageCode <> 'TERMINATED') group by project.industryCode")
    List<ProjectIndustryAggregate> summarizeCurrentIndustriesByProvinces(java.util.Set<UUID> provinceIds, boolean includeTerminated);

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
            where :includeTerminated = true or project.currentStageCode <> 'TERMINATED'
            group by project.currentStageCode
            """)
    List<ProjectStageAggregate> summarizeCurrentStagesNational(
            @Param("includeTerminated") boolean includeTerminated);

    @Query("""
            select project.cityId as cityId, count(project) as projectCount,
                   sum(project.totalAmount) as expectedAmount
            from ProjectEntity project
            where :includeTerminated = true or project.currentStageCode <> 'TERMINATED'
            group by project.cityId
            """)
    List<ProjectCityAggregate> summarizeCurrentCitiesNational(
            @Param("includeTerminated") boolean includeTerminated);

    @Query("""
            select project.industryCode as industryCode, count(project) as projectCount,
                   sum(project.totalAmount) as expectedAmount
            from ProjectEntity project
            where :includeTerminated = true or project.currentStageCode <> 'TERMINATED'
            group by project.industryCode
            """)
    List<ProjectIndustryAggregate> summarizeCurrentIndustriesNational(
            @Param("includeTerminated") boolean includeTerminated);

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
            where project.cityId = :cityId
              and (:includeTerminated = true or project.currentStageCode <> 'TERMINATED')
            group by project.industryCode
            """)
    List<ProjectIndustryAggregate> summarizeCurrentIndustriesByCity(
            @Param("cityId") UUID cityId, @Param("includeTerminated") boolean includeTerminated);
}
