package com.pluto.ndi.identity.infrastructure;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface UserRoleRepository extends JpaRepository<UserRoleEntity, UserRoleEntity.UserRoleId> {

    @Query("select role.id.roleCode from UserRoleEntity role where role.id.userId = :userId")
    List<String> findRoleCodesByUserId(UUID userId);
}
