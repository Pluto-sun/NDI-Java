# 切片 31：隔离恢复工具与操作规程

## 适用范围

本记录供维护人员演练和故障恢复使用。工具仅接受独立空数据库及空 MinIO 实例，不提供强制覆盖；目标存在表、视图、例程、事件或任意桶时拒绝。恢复窗口内不得启动目标应用或允许其他进程写入。工具不读取应用 datasource、MinIO 配置，不回退连接默认 3307 库。

## 准备与校验

在管理端下载归档，并单独保存管理记录中的 SHA-256；不要仅凭不可信归档自行计算的摘要建立信任。复制文件到受限目录，以环境或密钥工具注入下列专用凭据，不填写到命令参数、脚本和项目文档中。

| 环境变量 | 用途 |
| --- | --- |
| NDI_RESTORE_DB_USERNAME / NDI_RESTORE_DB_PASSWORD | 目标数据库专用账号、口令 |
| NDI_RESTORE_MYSQL_ROOT_PASSWORD | 仅用于 Docker 创建隔离目标的独立 root 口令 |
| NDI_RESTORE_MINIO_ACCESS_KEY / NDI_RESTORE_MINIO_SECRET_KEY | 目标 MinIO 专用凭据 |

```sh
./mvnw -DskipTests package
./scripts/restore-backup.sh verify \
  --archive /secure/ndi-backup-UUID.zip \
  --sha256 '<管理记录中的64位摘要>'
```

`verify` 不访问恢复目标。命令独立使用工程包内的 [PropertiesLauncher](https://docs.spring.io/spring-boot/3.5/specification/executable-jar/property-launcher.html)，不启动 Web、Flyway 或业务任务；正常 `java -jar` 入口仍为 NdiApplication。

## 创建隔离目标并恢复

下例仅用于本机演练。Compose 使用独立项目名、数据卷及 13318／19010 端口，不共享应用卷；凭据须在运行前注入。不要提前启动目标应用，否则自动迁移建表后将不再是空库。

```sh
docker compose -f docker/compose.restore.yml up -d --wait
export NDI_RESTORE_JDBC_URL='jdbc:mysql://127.0.0.1:13318/ndi_restore?serverTimezone=UTC'
export NDI_RESTORE_MINIO_ENDPOINT='http://127.0.0.1:19010'
./scripts/restore-backup.sh restore \
  --archive /secure/ndi-backup-UUID.zip \
  --sha256 '<管理记录中的64位摘要>' \
  --confirm-empty-target ndi_restore \
  --operator '<操作人姓名或工号>'
```

确认库名必须与 JDBC URL 完全一致，禁止 URL 内嵌凭据和危险驱动选项。命令先校验完整归档、SQL 操作白名单和两端空状态，再导入；SQL 流式执行，单句限 64 MiB。对象还原后逐项核对大小、类型、摘要及清单，数据库核对表行数和外键。

## 成功与失败处置

仅退出码 0 且报告状态为 RESTORED 才表示完成。旧会话全部失效，快照中的备份待处理／运行任务改为失败 RESTORED，避免重复执行；业务审批仍保留。恢复审计记录操作人和摘要。需要重新登录，并核查归档之后的账号、角色和密码变更。

退出码 2 表示参数、配置、归档或连接验证失败；3 表示非空或忙碌目标；4 表示恢复过程失败。MySQL DDL 不支持整库事务回滚，失败目标可能保留部分数据，工具不会自动删除。保留现场并另建空目标重试，不要清卷后假装恢复成功。

成功后先保持后台任务关闭，将应用的 `NDI_DATASOURCE_*`、`NDI_MINIO_*` 明确配置为目标，不能沿用默认 3307／9000 连接。`NDI_MINIO_PUBLIC_ENDPOINT` 须另设，主资料桶配置沿用原部署值。核对项目、金额、审批、进展、资料和统计，再按上线规程启用任务或切换流量。MinIO 凭据、私有访问策略、CORS 和部署配置不由数据归档复制，须独立配置。历史备份记录不代表旧归档文件一并迁移，原备份目录须另行保管。该工具不替代加密、异地副本和正式容灾演练。

## 核验来源

机制与断言对应 `BackupRestoreService`、`RestoreTargetMapper`、`RestoreSqlReader`、`RestoreSafetyTest` 和 `BackupArchiveIT`。文档技能知识库《高可用集群与故障转移》的“风险与边界”用于区分数据还原和生产可用性，不编造 RPO、RTO 指标。

## 验证结果（2026-10-21）

`mvn -q clean verify` 通过，52 项单元／契约／架构测试、100 项集成测试，无错误、失败或跳过。随后在宿主机 `LOADER_MAIN`、`LOADER_ARGS` 和应用 datasource 环境变量被故意干扰的情况下，针对性复跑再次通过，脚本仍只进入离线主类。

测试直接执行工程包与脚本，完成 verify、restore、空目标校验、非空库／非空对象实例拒绝和错误摘要拒绝。实际还原包含 6 MiB 分片对象，核对行数、外键、生成列、文本与二进制数据、会话失效及备份任务清理；恢复后的应用通过 Flyway／JPA 校验启动，并读取项目和限时资料下载，文件摘要一致。

正常入口的正式会话 10 条、开发态 6 条浏览器回归通过；前端格式和构建通过。恢复 Compose 已做静态配置校验，真实恢复使用 Testcontainers 隔离目标；未对本地 3307 应用库执行还原，也未启动 Compose 恢复服务。测试容器、数据及浏览器专用临时归档均已清理。
