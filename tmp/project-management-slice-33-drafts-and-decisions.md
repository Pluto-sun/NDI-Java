# 切片 33：草稿、撤销与驳回验收

## 范围与检查机制

本文供项目维护者核对迁移进度。本轮新增真实浏览器用例，不修改业务表或生产配置。执行环境仍为一次性 MySQL、MinIO 和应用，3307 业务库不参与。

草稿用例在必填项未齐时保存，检查草稿存在但正式项目查询无该项目；刷新后分别取消、确认载入，验证取消不覆盖当前输入、确认恢复原值。更新需求后再次刷新读取，并核对另一个账号的草稿列表不包含该记录；删除先取消、再确认，最终通过接口核对记录已移除。

审批用例由地市发起名称变更，取消撤销确认时保持待审，确认后记录为已撤销。新建第二条申请，由指定省分审批人尝试空意见驳回，页面提示必填且申请仍为待审；填写意见后驳回，读取正式项目名称和版本均保持原值。驳回后由发起人另建不同编号的申请，不复用旧单，并撤销清理。

以上检查同时读取页面反馈及真实接口数据；仅提示成功而业务状态未变化将判失败。素材使用清单：测试粒度参考内置《测试金字塔-Fowler》2.3 节；业务断言依据已确认规则和当前 API；范围差异依据旧版路由、服务和页面源码。验收数据不用于证明生产环境可上线。

## 新发现的功能差异

旧 `frontend/src/App.tsx` 的项目列表及 `backend/app/api/routers/projects.py` 提供签约主体、省分负责人、地市客户经理、产品单位支撑专家、项目日期起止筛选。当前 Java `ProjectController.findProjects` 和 `ProjectQuery` 尚无这些筛选，`ProjectsPage` 也未提供相应输入。旧服务按 `opportunity_date` 过滤日期，不能直接以新系统创建时间替代。

下一实施切片须补齐列表与导出共享筛选、界面和权限回归，并明确旧商机日期与新业务日期的字段映射；尚未具备对应日期模型时应补模型及历史映射，而非静默忽略筛选。本项纳入迁移差异清单，整体目标仍未达成。

旧 `project_service.py` 的筛选针对 `opportunity_date`；阶段推进通过及重大变更生效会更新该值，不能用阶段发生日期或最近追加进展时间代替。后续日期迁移须追溯旧值，并检查普通进展不会错误改变此筛选归属。

## 路由核对索引

本次按旧 `backend/app/api/routers` 对照新 Controller，确认功能入口的覆盖范围。下表为后续语义核对索引，入口存在不等于全部行为通过验收。

| 旧路由文件 | 新入口 | 当前证据／未闭合项 |
| --- | --- | --- |
| projects.py | ProjectController、ProjectDraftController、ProjectProgressController、ProjectStageController | 创建、草稿、进展与阶段已有浏览器用例；新增发现列表筛选缺项。 |
| changes.py | ProjectApprovalController、ProjectHistoryController、ProjectApprovalInboxController | 指定审批、组合变更、回退终止、撤销驳回已有用例。 |
| excel.py | ProjectImportController、ProjectExportController | 模板、映射、预检、行修正、确认、取消及导出已有接口；完整页面流程继续验收。 |
| dashboard.py、maps.py | ProjectWorkbenchController | 当前地图与动态跳转已验；历史月报数值和筛选组合继续验收。 |
| common.py | ProjectContextController、ProjectApprovalInboxController、ProjectInactivityController | 字典、已读计数及督办转发闭环已验，逐角色动态展示仍需扩大覆盖。 |
| auth.py、admin.py | IdentityAuthenticationController、IdentityAdministrationController、ProjectInactivityController | 正式会话、账号组织、多省映射、督办演练已有会话浏览器用例。 |
| storage.py | ProjectDocumentController、BackupController | 对象存储材料及完整备份已有用例；文件管理不沿用旧磁盘目录设计。 |
| operations.py | 不迁移 | 对应排产、计收台账、能力指标，依用户要求另行设计。 |

旧项目列表还提供导出字段勾选。此前用户同意首版固定摘要 CSV、最多 1000 条及操作审计，故不改回旧 Excel 导出；自选字段尚无对应入口，后续补齐时沿用已确认 CSV 格式和权限约束，不将首版范围视为整体功能已对等的证据。

## 外部边界依赖隔离

扩大回归时曾因外部地图边界服务不可用，导致要求 SVG 出现的用例失败。页面当时提示边界不可用且保留区域清单，不是项目统计失败。仅延长等待不能保证外部服务恢复，故将独立几何依赖隔离。

`scripts/e2e-map-boundary-server.mjs` 在本机 18081 提供明确合成的矩形 GeoJSON；`run-project-e2e.sh` 启动并健康检查，只在验收 Java 启动参数中覆盖边界地址，结束后回收进程。端口占用会直接拒绝启动，避免连接其他服务。正式配置和业务统计接口不变，权限、金额、动态、数据库及对象存储仍走真实应用。

该测试服务验证几何读取、地图交互和前后端连接，不证明实际行政边界的准确性或线上边界服务可达性；后者保留为部署检查项。此前实网失败和本轮合成几何通过不能互相替代，不以测试服务的可用性宣称线上依赖稳定。

## 执行结果（2026-10-22）

加入边界依赖隔离后，开发态回归 14 条通过，正式会话回归 10 条通过，均包含桌面与 375px。`npm run build`、`npm run format:check`、脚本语法检查和 `git diff --check` 通过。通过真实 Java 边界接口读取并核对三项合成几何；脚本退出后 18081 无监听，隔离容器已回收。

本轮未修改后端源码和迁移，未重跑 Maven 全套；不沿用旧测试数量作为本轮新结果。可复跑命令为 `NDI_E2E_SKIP_BUILD=1 scripts/run-project-e2e.sh`，正式会话增加 `NDI_E2E_AUTH_MODE=SESSION`；若后端包已变化则去掉跳过构建选项。日志位于 `/tmp/ndi-slice33-regression.log`、`/tmp/ndi-slice33-session.log`，详细运行产物位于项目 `tmp/e2e-*`，后续执行会覆盖。
