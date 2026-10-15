# 公共 API 组件边界

项目管理、排产、计收和能力指标模块会长期共享 HTTP 契约。公共组件应在首个业务接口前固定最小边界，减少前后端联调中出现的响应格式、错误含义和分页口径差异；业务状态、合同规则、排产约束和指标公式不得进入公共层。

## 成功响应

业务 Controller 显式返回 `ApiResponse<T>`，不使用全局 `ResponseBodyAdvice` 自动包裹。自动包裹会破坏文件下载、204 响应、Actuator 和后续 OpenAPI 生成。建议字段为 `code`、`message`、`data`、`requestId`、`timestamp`，其中成功请求的 `code` 固定为 `OK`。HTTP 状态码表达协议结果，`code` 表达稳定的业务契约。

分页使用独立的 `PageResponse<T>`，字段固定为 `items`、`page`、`size`、`totalElements`、`totalPages`，其中 `page` 采用零基序号，与 Spring Data 的分页请求保持一致。不将 Spring Data 的 `Page` 直接暴露给客户端，也不在第一阶段引入游标分页；报表和导出走异步任务，不伪装成无限列表接口。

## 错误响应

失败响应继续使用 Spring `ProblemDetail`，并扩展 `code`、`requestId` 和 `errors`。这使 HTTP 客户端保留标准 `status`、`title`、`detail`、`instance` 字段，同时前端依据稳定的 `code` 展示提示或执行分支。参数校验错误的 `errors` 只输出字段名和安全的中文提示，不返回口令、令牌、原始异常栈或敏感输入值。

错误码采用可读字符串而非无语义数字，例如 `COMMON_INVALID_ARGUMENT`、`COMMON_VALIDATION_FAILED`、`COMMON_CONFLICT`、`AUTH_UNAUTHENTICATED`、`AUTH_FORBIDDEN`、`PROJECT_NOT_FOUND`、`PROJECT_INVALID_STATE`。每个错误码只绑定一种 HTTP 状态和一种对外含义；枚举按模块维护，禁止把业务错误持续追加到 `shared`。

## 异常与配置

新增带有 `ErrorCode` 的 `BusinessException`，由全局异常处理器统一转换。处理范围包括业务异常、`MethodArgumentNotValidException`、约束校验异常、请求体不可读、参数类型错误、认证失败、权限拒绝和未处理异常。Spring Security 在进入 Controller 前产生的认证与授权失败，通过同一 `ProblemDetail` 写入器返回。未处理异常记录完整日志并返回 `COMMON_INTERNAL_ERROR`，避免向客户端泄露实现细节。

配置采用带 `@Validated` 的 `@ConfigurationProperties` 对象承载业务化配置；数据库、对象存储、认证和导入限制均由环境变量覆盖。公共层提供 `Clock`，业务代码不得直接调用系统时间，以支持期间计算和测试替身。认证模块落地后再提供 `CurrentActor` 与审计接口，当前不预设用户表或令牌实现。

## 实施顺序

1. 新增 `ApiResponse`、`PageResponse`、`ErrorCode`、`BusinessException` 与统一异常处理器。
2. 为成功响应、校验失败、未认证、无权限、资源不存在及未知异常编写 MockMvc 测试，并断言 `X-Request-Id` 与响应体一致。
3. 在 `projects` 的首个查询和命令接口中使用该契约；确认前端调用方式后再向排产、计收和能力指标模块复用。
4. 认证与组织数据范围完成后，将 `AUTH_*` 错误码和 `CurrentActor` 接入安全链路。

公共组件只承担协议和技术横切能力。领域规则始终留在对应业务模块的 `domain` 与 `application` 层。
