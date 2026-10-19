package com.pluto.ndi.shared.security;

import java.util.UUID;

/** 已完成认证的业务账号标识。 */
public record ActorPrincipal(UUID userId) {
}
