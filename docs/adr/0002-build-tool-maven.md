# ADR-0002：采用 Maven 作为 NDI Java 的构建工具

**日期：** 2026-10-15
**状态：** 已采纳

## 背景

NDI Java 将承载项目管理、排产、计收和能力指标等长期演进的业务模块。构建工具需要统一依赖版本、测试门禁、数据库迁移验证、打包方式及持续集成执行方式，并降低开发成员机器配置差异带来的风险。

## 决策

项目采用 **Apache Maven** 管理构建、依赖、测试和打包。首发阶段采用单个 Spring Boot 应用的根 `pom.xml`，不因四个业务模块而过早拆成 Maven 多模块工程；业务边界由 Spring Modulith 与架构测试约束。出现独立交付、独立版本、独立团队所有权等确定需求后，再将边界稳定的模块提升为 Maven 子模块或独立服务。

仓库提交 Maven Wrapper（`mvnw`、`mvnw.cmd`、`.mvn/wrapper/`），本地与 CI 统一执行 `./mvnw`。全局 `mvn` 仅作为生成 Wrapper 或本机辅助工具，不构成项目构建前提。

## 构建基线

| 项目 | 决定 |
|---|---|
| JDK | Java 21 LTS，Maven Enforcer 在构建入口校验版本 |
| Maven | 由 Maven Wrapper 固定；系统 Maven 满足 Spring Boot 最低要求即可 |
| POM 模式 | `spring-boot-starter-parent` 管理 Spring Boot 依赖和插件默认配置 |
| 依赖版本 | 由 Spring Boot BOM 管理；自定义版本集中在根 POM 的 `properties`，禁止散落在依赖声明中 |
| 质量门禁 | Maven Enforcer、Spotless、Checkstyle、Surefire、Failsafe、JaCoCo、架构测试 |
| 数据库验证 | Flyway 迁移在 Testcontainers MySQL 中从空库回放 |
| 打包 | Spring Boot 可执行 JAR；发布阶段使用构建插件生成不可变容器镜像 |

建议的根 POM 插件职责如下：`maven-compiler-plugin` 固定 `release=21`；`maven-enforcer-plugin` 限制 JDK、Maven 与依赖收敛；`maven-surefire-plugin` 运行单元测试；`maven-failsafe-plugin` 运行以 `*IT` 命名的集成测试；`spring-boot-maven-plugin` 负责打包；格式化、静态检查和覆盖率插件接入 CI 的 `verify` 阶段。数据库与对象存储均由配置文件区分 local、test、prod 环境，业务代码不根据环境写分支。

## 后果

采用 Maven 后，团队成员获得清晰的生命周期入口：`./mvnw test` 用于快速验证，`./mvnw verify` 用于执行完整质量门禁，`./mvnw spring-boot:run -Dspring-boot.run.profiles=local` 用于本地启动。GitHub Actions 使用同一 Wrapper，构建行为与开发机保持一致。

Maven 多模块工程暂不启用，避免构建结构替代业务架构。模块之间的可见性和依赖关系由 Spring Modulith 测试约束；当确需拆分构建单元时，必须新增 ADR，说明所有权、发布节奏、数据边界、版本兼容策略和回滚方案。

## 当前环境核验

2026-10-15 检测结果：本地仓库使用 `main` 分支并关联远端 `git@github.com:Pluto-sun/NDI-Java.git`。加载 `/Users/alexander/.zshrc` 后，终端使用 IntelliJ IDEA 自带的 Apache Maven 3.9.16；当前 Maven 运行在 OpenJDK 21.0.12.1 上。

首次 Maven Wrapper 已使用当前已验证的 Maven 3.9.16 生成；此后日常构建统一使用仓库中的 `./mvnw`。
