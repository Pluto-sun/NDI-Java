# 本地 IDEA 启动时的数据库连接说明

## 现象与原因

IDEA 直接启动 `NdiApplication` 时出现 `Access denied for user 'ndi'@'172.17.0.1'`，表示 JDBC 已连接到 MySQL 网络端口，认证阶段被服务端拒绝。`172.17.0.1` 是 Docker 网络识别到的主机侧来源地址，不代表应用错误连接到远程数据库。

当前宿主机 `3306` 端口由旧项目的 `mysql80` 容器占用；NDI 的 `docker/compose.yml` 服务尚未运行。因此 `application-local.yml` 的默认 JDBC 地址 `localhost:3306/ndi` 命中了旧项目数据库，其用户密码与 NDI 本地配置不一致。

## 推荐运行方式

NDI 使用独立 MySQL 容器，不复用或修改 `mysql80`。仓库的 Compose 已将宿主机映射设置为 `3307:3306`，`application-local.yml` 的默认 JDBC 地址已设置为 `jdbc:mysql://localhost:3307/ndi`。用户名保持 `ndi`，本地开发密码保持 `ndi_local_password`，两处配置保持一致。

调整后执行：

```bash
./scripts/start-local-mysql.sh
./mvnw spring-boot:run
```

`/Users/alexander/Documents/docker_scripts/启动NDI-MySQL.command` 是当前机器的双击入口，内部调用仓库脚本。

IDEA Run Configuration 未设置 `NDI_DATASOURCE_URL` 时会使用该默认地址。若某个开发环境端口不同，优先在 Run Configuration 配置 `NDI_DATASOURCE_URL`、`NDI_DATASOURCE_USERNAME` 和 `NDI_DATASOURCE_PASSWORD`，无需修改并提交本地 YAML。

## 不建议的处理方式

不要为消除认证错误而把 NDI 的数据库账号改为旧容器账号，也不要清理或重置旧项目的 `mysql80` 数据卷。两个项目的数据库、用户和迁移历史应相互隔离。
