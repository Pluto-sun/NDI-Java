package com.pluto.ndi.shared.security;

import java.util.Optional;
import java.util.UUID;

/** 由身份模块解析会话；安全基础设施不依赖具体账号持久化实现。 */
public interface ActorSessionDirectory {
    Optional<UUID> resolveActor(String sessionToken);
}
