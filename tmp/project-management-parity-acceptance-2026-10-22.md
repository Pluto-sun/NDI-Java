# 项目管理功能对等验收

## 1. 概述

日期：2026-10-22。实现基线：`6d22254`；附加地图联网验收见本次提交。用途：内部留档与后续开发交接。

结论：项目管理及必要平台功能已完成复现，按用户确认的规则调整旧行为。排产、独立计收、能力指标、旧库数据迁移不在本轮范围；项目的“计收”阶段仍属项目管理。结论不代表生产发布验收。

## 2. 方法

逐项对照旧 `backend/app/api/routers/`、`frontend/src/App.tsx` 与 Java 控制器、服务、测试。接口路径重设计，操作能力按下表追踪。数值、权限和并发以隔离 MySQL 集成测试验证，页面以真实服务及 MinIO 验证。通过准则：范围内功能均有实现及验证，失败、越权、审批前后状态均符合规则。

路径缩写：`C=src/main/java/com/pluto/ndi/projects/controller/`，`I=src/test/java/com/pluto/ndi/integration/`，`E=frontend/e2e/`。表中控制器省略 `.java`；测试名省略所在目录及扩展名。

## 3. 功能与证据

| 旧路由文件及操作（省略路由前缀） | 新实现 | 集成／浏览器证据 |
| --- | --- | --- |
| projects：GET duplicate-check；GET/POST 根路径；GET {id} | C/ProjectController | I/ProjectApprovalIT；E/project-filters.spec.ts、project-list-summary.spec.ts |
| projects：GET/POST drafts；PUT drafts/{id} | C/ProjectDraftController | I/ProjectApprovalIT；E/drafts-and-decisions.spec.ts |
| projects：POST {id}/progresses | C/ProjectProgressController | I/ProjectApprovalIT；E/lifecycle.spec.ts、inactivity.spec.ts |
| projects：PATCH {id}/responsibilities | C/ProjectController 的 profile | I/ProjectApprovalIT；E/disabled-leader.spec.ts |
| projects：POST {id}/stage-transitions | C/ProjectStageController | I/ProjectApprovalIT；E/lifecycle.spec.ts |
| changes：POST projects/{id}/change-requests；GET change-requests、change-requests/{id} | C/ProjectApprovalController、ProjectApprovalInboxController | I/ProjectApprovalIT、ProjectFieldChangesIT；E/major-change.spec.ts、approval-records.spec.ts |
| changes：POST change-requests/{id}/decision、cancel | C/ProjectApprovalController | I/ProjectApprovalIT；E/drafts-and-decisions.spec.ts |
| common：GET dictionaries | C/ProjectContextController | ProjectContextServiceTest；E/lifecycle.spec.ts |
| common：GET notifications/unread-count、notifications；POST notifications/{id}/read | C/ProjectApprovalInboxController、ProjectInactivityController | I/ReviewerProvinceScopeIT、ProjectInactivityIT；E/notifications.spec.ts、inactivity.spec.ts |
| common：GET inactivity-history；GET inactivity-cases/{id}/recipients；POST inactivity-cases/{id}/forward | C/ProjectInactivityController | I/ProjectInactivityIT；E/inactivity.spec.ts |
| storage：GET/POST projects/{id}/attachments；GET attachments/{id}/download；DELETE attachments/{id} | C/ProjectDocumentController、documents 模块 | I/ProjectApprovalIT、documents/service/MinioObjectStorageIT；E/read-only-documents.spec.ts、lifecycle.spec.ts |
| storage：GET/POST admin/backups | backup/controller/BackupController | I/BackupTaskIT、BackupArchiveIT；E/session/backups.spec.ts |
| excel：POST imports/inspect、imports/preview；GET imports/template | C/ProjectImportController | I/ProjectImportIT；E/import-projects.spec.ts |
| excel：PUT imports/{id}/rows/{row}；GET imports/{id}/failed-rows/export | C/ProjectImportController | I/ProjectImportIT；E/import-projects.spec.ts |
| excel：GET imports、imports/{id}；POST imports/{id}/confirm；DELETE imports/{id} | C/ProjectImportController | I/ProjectImportIT；E/import-projects.spec.ts |
| excel：GET exports/projects | C/ProjectExportController，任务式 CSV | I/ProjectApprovalIT；E/export-fields.spec.ts、project-dates.spec.ts |
| dashboard：GET 根路径、summary、trend、map | C/ProjectWorkbenchController、ProjectAttributionController | I/ProjectAttributionIT、ProjectMonthlySnapshotIT；E/attribution-workbench.spec.ts、workbench-history.spec.ts、map-attribution-range.spec.ts、recent-activities.spec.ts、workbench-refresh.spec.ts |
| maps：GET {adcode} | C/ProjectWorkbenchController 的 map/boundaries/{adcode} | E/workbench.spec.ts 联网模式；map-stage-filter.spec.ts |
| auth：POST 根路径；GET/DELETE current；PATCH current/password、current/profile | identity/controller/IdentityAuthenticationController | I/IdentitySessionIT；E/session/account.spec.ts |
| admin：GET configuration；POST users、cities；PUT users/{id}；PATCH users/{id}/enabled；POST users/{id}/reset-password | identity/controller/IdentityAdministrationController | I/IdentityAdministrationIT、ZeroDataProjectIT；E/session/administration.spec.ts、provinces.spec.ts |
| admin：PUT users/{id}/reviewer-provinces、reviewer-province-maps | identity/controller/IdentityAdministrationController | I/ReviewerProvinceScopeIT；E/session/reviewer-provinces.spec.ts |
| admin：POST inactivity-reminders/trigger | C/ProjectInactivityController | I/ProjectInactivityIT；E/session/inactivity-administration.spec.ts |
| main：GET /api/v1/health；统一错误／请求编号 | /actuator/health；shared/web | run-project-e2e.sh 就绪检查；ApiWebContractTest、ApiSecurityContractTest |

| 已确认规则／扩展 | 证据 |
| --- | --- |
| 五阶段；指定唯一审批人；48 小时；管理员不审批 | ProjectApprovalIT、IdentityPermissionService；E/lifecycle.spec.ts、approval-records.spec.ts |
| 单项目单待审；版本冲突；驳回需意见；撤销留痕；24 小时逾期提醒 | ProjectApprovalIT、ProjectApprovalReminderWriter；E/drafts-and-decisions.spec.ts、operation-audit.spec.ts |
| 回退清无效合同；终止保留合同且只读；不恢复、不合并 | ProjectApprovalIT、ProjectFieldChangesIT；E/lifecycle.spec.ts |
| 总额必填；已选分类金额必填且合计不超总额 | ProjectServiceTest、ProjectApprovalIT、ProjectImportIT |
| 角色及省市范围；责任文本不授权；接口人追加进展、资料和导入 | ReviewerProvinceScopeIT、ProjectApprovalIT；E/read-only-documents.spec.ts、recent-activities.spec.ts |
| 所属月份区间唯一汇总；默认排除终止；与月末快照分开 | ProjectAttributionIT；E/attribution-workbench.spec.ts、map-attribution-range.spec.ts |
| 上月月末快照；迟批只修订相关项目；保留历史分类及原因 | ProjectMonthlySnapshotIT、ProjectFieldChangesIT、ProjectMonthlySnapshotJob；E/workbench-history.spec.ts |
| MinIO 资料；审批材料保护；已读与督办处理分离 | ProjectApprovalIT、ProjectInactivityIT；E/lifecycle.spec.ts、inactivity.spec.ts |
| 重复导入留首条；合同校验；重要度、两类专家分别保留 | ProjectImportIT、ProjectImportAttributesTest；E/import-projects.spec.ts |
| 空库建目录、账号及项目；模块边界 | ZeroDataProjectIT、ApplicationArchitectureTest |

### 执行记录

| 验证 | 结果 | 本机日志 |
| --- | --- | --- |
| Maven 全量 | 71 单元等＋120 集成，共191；失败、错误、跳过均0 | `/tmp/ndi-import-attributes-verify.log` |
| 追加兼容断言复验 | 6 通过；升级及旧计划默认值 | `/tmp/ndi-import-attributes-compat-verify.log` |
| LOCAL_ACTOR | 54 通过；1440px／375px | `/tmp/ndi-import-attributes-e2e.log` |
| SESSION | 12 通过；1440px／375px | `/tmp/ndi-import-attributes-session-e2e.log` |
| 真实地图联网 | 4 通过；含全国 MultiPolygon、省分下钻 | `/tmp/ndi-live-map-e2e.log` |
| 默认地图模式复验 | 4 通过；未启用外网开关 | `/tmp/ndi-map-default-regression.log` |
| 构建／格式／差异检查 | `build`、`format:check`、`git diff --check` 通过 | 构建输出／Git |

复跑时串行执行打包与浏览器验收，避免覆盖运行中的 jar。以下命令使用隔离库；不执行演示 SQL 到日常库。

```sh
./mvnw -q verify
(cd frontend && npm run build && npm run format:check)
NDI_E2E_SKIP_BUILD=1 ./scripts/run-project-e2e.sh
NDI_E2E_SKIP_BUILD=1 NDI_E2E_AUTH_MODE=SESSION ./scripts/run-project-e2e.sh
NDI_E2E_SKIP_BUILD=1 NDI_E2E_LIVE_MAP=1 ./scripts/run-project-e2e.sh workbench.spec.ts
git diff --check
```

## 4. 结论与后续边界

范围内功能通过验收。用户确认的差异包括 CSV 自选字段导出、管理员不参与业务审批、唯一审批人、终止只读、月末快照与所属月份并存。未遗留本轮功能缺口。

生产上线仍须单独验收登录限流、失败审计、会话清理、TLS／Cookie／CSRF 配置、备份加密与异地副本、负载容量和恢复演练；本轮未宣称其完成。地图上游中断时保留区域清单。日志位于本机 `/tmp`，可能被清理，复跑命令与测试源码用于重建证据。

正常后端重启将执行 V36；本轮未写入或重启日常3307库。后续三个模块各自设计工作台，通过服务接口及明确的标识关联项目，避免直接耦合旧台账。

核验来源：旧路由与页面、表内测试、当前服务实现及执行日志。编写时按《测试金字塔-Fowler》2.3区分验证粒度；项目事实均取自仓库及运行结果。旧 `tmp` 状态文档按日期保留，本记录更新其验收结论。
