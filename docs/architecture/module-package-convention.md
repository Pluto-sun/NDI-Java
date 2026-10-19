# 业务模块包结构约定

## 1. 适用范围

本约定适用于 `projects`、`production`、`collection`、`capabilities` 及其直接支撑的 `identity`、`organization` 模块。`shared` 是跨模块技术组件，不复制业务分层；`documents`、`workflow`、`reporting` 在新增业务用例时同样遵守本约定。

每个模块以 `com.pluto.ndi.<module>` 为根包，并按照职责组织代码，而不是以接口、应用、基础设施等抽象分层作为目录名。尚未产生对应职责代码时不创建空目录。

| 包 | 职责 |
| --- | --- |
| `config` | 模块专属的 Spring 配置、属性绑定、拦截器及 Bean 装配。禁止放置业务规则。 |
| `controller` | HTTP 接口、请求与响应对象、参数校验和鉴权入口。不得直接访问仓储。 |
| `domain` | 实体、值对象、枚举、错误码和领域规则；JPA 实体放入 `domain.entity`。 |
| `mapper` | 请求对象、领域对象、响应对象之间的显式转换。不得承载查询或写库逻辑。 |
| `service` | 用例编排、事务边界、权限校验和跨模块服务契约。 |
| `util` | 无业务状态的纯工具，如编号生成、格式转换；不得依赖 Controller 或 Repository。 |
| `repository` | JPA Repository、数据库查询和持久化接口。该层是本项目使用 JPA 所必需的补充，不以 `mapper` 替代。 |

## 2. 调用方向

Controller 只调用本模块 Service；Service 协调 Domain、Mapper、Repository，并调用其他模块公开的 Service 契约；Repository 只操作本模块 Domain Entity。Mapper 不调用 Repository，Util 不承担权限、事务或业务状态变更。

跨模块访问仍受 Spring Modulith 约束。其他模块只能依赖目标模块经 `service` 包公开的契约，不能引用其 `domain.entity`、`repository`、`mapper` 或 Controller。模块内类型因 JPA 分层需要使用 `public` 可见性时，仍通过包边界和架构测试限制跨模块引用。

## 3. 当前落地

`projects` 已按本约定拆分为 Controller、Domain、Mapper、Repository、Service 和 Util；`identity` 与 `organization` 已拆分为 Domain、Repository 和 Service，并通过 `service` 包向项目模块提供账号资格、数据范围和组织范围查询。当前没有模块专属配置或 HTTP 接口的模块，不创建空的 `config` 或 `controller` 包。

后续排产、计收和能力指标模块新增用例时，应先建立本约定中的包结构，再提交实体、接口和迁移。代码审查中发现 Controller 直连 Repository、Service 访问其他模块内部包或 Mapper 包含业务规则时，应作为结构问题处理。
