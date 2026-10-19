package com.pluto.ndi.identity.repository;

import java.util.UUID;

import com.pluto.ndi.identity.domain.entity.UserAccountEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAccountRepository extends JpaRepository<UserAccountEntity, UUID> {
}
