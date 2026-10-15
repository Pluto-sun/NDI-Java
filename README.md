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
