package com.pluto.ndi.documents.repository;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

import com.pluto.ndi.documents.domain.entity.DocumentEntity;
import com.pluto.ndi.documents.domain.DocumentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface DocumentRepository extends JpaRepository<DocumentEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select document from DocumentEntity document where document.id = :id")
    Optional<DocumentEntity> findLockedById(UUID id);

    List<DocumentEntity> findByScopeTypeAndScopeIdAndStatusOrderByCreatedAtDesc(
            String scopeType, UUID scopeId, DocumentStatus status);
}
