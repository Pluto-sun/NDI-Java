# 架构总览

NDI Java 以项目管理、排产、计收和能力指标为核心业务域，采用单个 Spring Boot 部署单元与 Spring Modulith 包边界。四个业务模块共享项目、组织、权限与审批上下文，附件由 `documents` 模块提供统一能力，首发阶段保留本地事务一致性，避免把尚未稳定的业务规则转化为跨服务调用和最终一致性问题。

模块内部使用 `api`、`application`、`domain`、`infrastructure` 四层组织；当前仅创建模块入口，实际用例实现时按该结构展开。`projects` 是后续业务归属主线，`production`、`collection`、`capabilities` 仅保存项目标识及必要快照。`reporting` 消费公开事件构建读模型，承载四模块趋势、汇总和驾驶舱；工作台待办与录入入口仍归属对应业务模块。`documents` 将文档内容存入 MinIO，对象元数据与关联关系留在 MySQL，不回写交易数据。

数据库结构只允许经 Flyway 迁移演进，JPA 在各环境均使用 `validate`，不允许应用启动时自动建表或修改表。首个迁移只建立技术审计表，避免在排产约束、计收口径和指标公式尚未澄清前生成难以兼容的业务表。

安全入口默认拒绝全部业务请求，仅暴露健康检查；身份认证、组织数据范围和业务权限完成设计后逐项开放 API。请求链路使用 `X-Request-Id` 写入响应和日志上下文，便于后续接入集中日志与审计平台。

测试采用由快到慢的分层方式：模块架构测试在普通测试阶段运行，Testcontainers MySQL 集成测试在 `verify` 阶段执行，GitHub Actions 使用 Java 21 与同一 Maven Wrapper 验证。详细的构建决定见 [ADR-0002](../adr/0002-build-tool-maven.md)。
