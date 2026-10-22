package com.pluto.ndi.organization.repository;

import java.util.UUID;
import java.util.Optional;
import java.util.List;

import com.pluto.ndi.organization.domain.entity.ProvinceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProvinceRepository extends JpaRepository<ProvinceEntity, UUID> {

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select province from ProvinceEntity province where province.id = :id")
    Optional<ProvinceEntity> lockById(UUID id);

    boolean existsByIdAndEnabledTrue(UUID id);

    boolean existsByProvinceName(String name);

    Optional<ProvinceEntity> findByProvinceNameAndEnabledTrue(String provinceName);

    List<ProvinceEntity> findByEnabledTrueOrderByProvinceNameAsc();
}
