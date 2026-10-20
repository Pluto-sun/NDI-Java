package com.pluto.ndi.projects.repository;

import java.util.Optional;
import java.util.UUID;

import com.pluto.ndi.projects.domain.entity.ProjectDraftEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectDraftRepository extends JpaRepository<ProjectDraftEntity, UUID> {
    Page<ProjectDraftEntity> findByOwnerIdOrderByUpdatedAtDescIdDesc(UUID ownerId, Pageable pageable);
    Optional<ProjectDraftEntity> findByIdAndOwnerId(UUID id, UUID ownerId);
}
