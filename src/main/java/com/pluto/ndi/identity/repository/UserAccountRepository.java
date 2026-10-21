package com.pluto.ndi.identity.repository;

import java.util.UUID;
import java.util.List;

import com.pluto.ndi.identity.domain.entity.UserAccountEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface UserAccountRepository extends JpaRepository<UserAccountEntity, UUID> {

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select account from UserAccountEntity account where account.loginName = :loginName")
    java.util.Optional<UserAccountEntity> lockByLoginName(String loginName);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select account from UserAccountEntity account where account.id = :id")
    java.util.Optional<UserAccountEntity> lockById(UUID id);

    boolean existsByEmailAndIdNot(String email, UUID id);
    boolean existsByLoginNameAndIdNot(String loginName, UUID id);

    @Query("select account from UserAccountEntity account where (:enabled is null or account.enabled = :enabled) "
            + "and (:term = '' or lower(account.loginName) like :pattern escape '!' "
            + "or lower(account.displayName) like :pattern escape '!' or lower(account.email) like :pattern escape '!')")
    org.springframework.data.domain.Page<UserAccountEntity> search(String term, String pattern, Boolean enabled,
            org.springframework.data.domain.Pageable pageable);

    @Query(value = "select id from identity_administration_lock where id = 1 for update", nativeQuery = true)
    int lockAdministration();

    @Query("select count(account) from UserAccountEntity account, UserRoleEntity role "
            + "where account.id = role.id.userId and account.enabled = true and account.passwordHash is not null "
            + "and role.id.roleCode = 'SYSTEM_ADMIN'")
    long countUsableAdministrators();

    List<UserAccountEntity> findByDisplayNameAndProvinceIdAndEnabledTrue(String displayName, UUID provinceId);

    List<UserAccountEntity> findByEnabledTrueOrderByDisplayNameAsc();

    List<UserAccountEntity> findByProvinceIdAndEnabledTrueOrderByDisplayNameAsc(UUID provinceId);

    List<UserAccountEntity> findByCityIdAndEnabledTrueOrderByDisplayNameAsc(UUID cityId);

    @Query("select distinct account from UserAccountEntity account, UserRoleEntity role "
            + "where account.id = role.id.userId and account.enabled = true and role.id.roleCode in :roles "
            + "order by account.displayName")
    List<UserAccountEntity> findEnabledByRoles(java.util.Collection<String> roles);
}
