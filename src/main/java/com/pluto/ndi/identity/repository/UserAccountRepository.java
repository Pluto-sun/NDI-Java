package com.pluto.ndi.identity.repository;

import java.util.UUID;
import java.util.List;

import com.pluto.ndi.identity.domain.entity.UserAccountEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAccountRepository extends JpaRepository<UserAccountEntity, UUID> {

    List<UserAccountEntity> findByDisplayNameAndProvinceIdAndEnabledTrue(String displayName, UUID provinceId);
}
