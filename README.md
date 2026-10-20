# NDI Java

NDI 是面向项目管理、排产、计收与能力指标的企业业务平台。本仓库采用 Java 21、Spring Boot、Spring Modulith、MySQL 与 Flyway 构建模块化单体；模块边界通过架构测试持续校验。

## 本地启动

加载 shell 配置后执行以下命令：

```bash
source /Users/alexander/.zshrc
./scripts/start-local-mysql.sh
./mvnw spring-boot:run
```

应用启动后访问 `http://localhost:8080/actuator/health`。NDI 本地 MySQL 映射至 `localhost:3307`，本地数据库账号仅用于 Docker 开发环境。`/Users/alexander/Documents/docker_scripts/启动NDI-MySQL.command` 是当前机器的双击入口，调用仓库中的启动脚本。MinIO 在需要附件功能时通过 `docker compose -f docker/compose.yml up -d minio` 启动；生产配置从 `NDI_DATASOURCE_URL`、`NDI_DATASOURCE_USERNAME`、`NDI_DATASOURCE_PASSWORD` 注入。

## 项目管理前端联调

本仓库的 `frontend/` 是项目管理独立切片，开发服务器监听 `127.0.0.1:5173` 并代理 `/api` 至 `127.0.0.1:8080`。首次使用空数据库时，先启动 Java 服务完成 Flyway 迁移，再执行本地演示数据脚本；该脚本仅操作本仓库 Docker Compose 的 MySQL，不属于生产迁移。

```bash
./scripts/seed-local-project-demo.sh
cd frontend
npm ci
npm run dev
```

打开 `http://127.0.0.1:5173/`，从演示账号中选择省分负责人、地市负责人或接口人。`local` Profile 下的账号选择与 `X-NDI-Actor-Id` 请求头仅用于开发测试，Java 服务在该 Profile 下只绑定 `127.0.0.1`；生产环境不会注册演示账号选择接口，正式认证尚未接入。当前前端已接通项目工作台、列表、创建、详情、进展、里程碑、资料上传、阶段推进、重大变更和审批待办。导入导出、进展更正及历史工作台页面仍按后续切片推进。

## 常用命令

```bash
./mvnw test
./mvnw verify
./mvnw package
docker build -f docker/Dockerfile -t ndi:local .
```

`verify` 会执行架构测试、单元测试、集成测试和 Flyway 迁移回放。集成测试使用 Testcontainers 启动 MySQL；本机未运行 Docker 时对应测试被跳过，GitHub Actions 在 Docker Runner 上执行完整验证。

## 模块边界

`identity`、`organization`、`projects`、`workflow`、`documents`、`production`、`collection`、`capabilities`、`reporting` 是一级应用模块。跨模块访问只能使用对方公开的应用接口或已发布领域事件，禁止引用其他模块的持久化实体和 Repository。

详细设计与决策记录位于 [docs](docs)。
