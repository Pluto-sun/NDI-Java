package com.pluto.ndi.identity.repository;

import java.util.UUID;
import java.util.List;

import com.pluto.ndi.identity.domain.entity.UserAccountEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface UserAccountRepository extends JpaRepository<UserAccountEntity, UUID> {

    List<UserAccountEntity> findByDisplayNameAndProvinceIdAndEnabledTrue(String displayName, UUID provinceId);

    List<UserAccountEntity> findByEnabledTrueOrderByDisplayNameAsc();

    List<UserAccountEntity> findByProvinceIdAndEnabledTrueOrderByDisplayNameAsc(UUID provinceId);

    List<UserAccountEntity> findByCityIdAndEnabledTrueOrderByDisplayNameAsc(UUID cityId);

    @Query("select distinct account from UserAccountEntity account, UserRoleEntity role "
            + "where account.id = role.id.userId and account.enabled = true and role.id.roleCode in :roles "
            + "order by account.displayName")
    List<UserAccountEntity> findEnabledByRoles(java.util.Collection<String> roles);
}
