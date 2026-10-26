package com.pluto.ndi.organization.service;

import java.util.UUID;
import com.pluto.ndi.organization.domain.OrganizationErrorCode;
import com.pluto.ndi.shared.web.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 管理员入口完成鉴权后调用，组织模块不反向依赖账号模块。 */
@Service
public class OrganizationAdministrationService {

    /** 保留旧接口的明确拒绝响应，目录只通过版本化迁移维护。 */
    @Transactional(propagation = Propagation.MANDATORY)
    public ProvinceOption createProvince(UUID actorId, String name) {
        throw new BusinessException(OrganizationErrorCode.CATALOG_READ_ONLY);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public CityOption createCity(UUID actorId, UUID provinceId, String name) {
        throw new BusinessException(OrganizationErrorCode.CATALOG_READ_ONLY);
    }
}
