package com.pluto.ndi.identity.repository;

import java.util.UUID;

/** 批量查询账号角色所用的只读结果。 */
public record UserRoleAssignment(UUID userId, String roleCode) { }
