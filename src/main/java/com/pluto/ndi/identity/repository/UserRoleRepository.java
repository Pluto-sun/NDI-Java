package com.pluto.ndi.identity.repository;

import java.util.List;
import java.util.UUID;
import java.util.Collection;

import com.pluto.ndi.identity.domain.entity.UserRoleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface UserRoleRepository extends JpaRepository<UserRoleEntity, UserRoleEntity.UserRoleId> {

    @org.springframework.data.jpa.repository.Modifying(flushAutomatically = true)
    @Query("delete from UserRoleEntity role where role.id.userId = :userId")
    void deleteByUserId(UUID userId);

    @Query("select role.id.roleCode from UserRoleEntity role where role.id.userId = :userId")
    List<String> findRoleCodesByUserId(UUID userId);

    @Query("select new com.pluto.ndi.identity.repository.UserRoleAssignment(role.id.userId, role.id.roleCode) "
            + "from UserRoleEntity role where role.id.userId in :userIds")
    List<UserRoleAssignment> findAssignmentsByUserIds(Collection<UUID> userIds);
}
