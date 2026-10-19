package com.pluto.ndi.projects.infrastructure;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectChangeEventRepository extends JpaRepository<ProjectChangeEventEntity, UUID> {
}
