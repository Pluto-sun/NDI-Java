# MinIO 容器安装与启动说明

## 镜像选择

项目固定使用 `minio/minio:RELEASE.2023-09-30T07-02-29Z`，不使用 `latest`。该版本的镜像构建基线为 UBI 8，比基于 UBI 9 的后续镜像更贴近 CentOS 7 的 Docker 部署环境。固定版本避免开发、测试和 CentOS 7 部署环境因镜像自动升级产生不一致。

容器镜像基线不是 CentOS 7 官方兼容性承诺。部署前必须在目标服务器验证 Docker Engine、内核、存储驱动、SELinux 与防火墙策略，并完成上传、下载和重启后对象仍可读取的验收。CentOS 7 已停止维护，正式上线前应制定操作系统升级计划；升级操作系统后，应同步评估更新的 MinIO 镜像版本。

项目的单节点 MinIO 用于当前附件开发与单实例部署。若生产环境需要多节点高可用、跨主机容灾或更高的可用性目标，应独立设计分布式对象存储部署与备份恢复方案，不能把本 Compose 文件直接作为集群方案。

## 操作命令

在项目根目录执行以下命令。第一次使用先拉取镜像：

```bash
./scripts/pull-minio-image.sh
```

等价的 Docker 命令如下：

```bash
docker pull minio/minio:RELEASE.2023-09-30T07-02-29Z
```

启动容器并等待健康检查：

```bash
./scripts/start-local-minio.sh
```

启动脚本实际执行的容器编排命令如下：

```bash
docker compose --project-directory . -f docker/compose.yml up -d minio
```

启动成功后，S3 兼容服务地址为 `http://127.0.0.1:9000`，管理控制台地址为 `http://127.0.0.1:9001`。数据存放在 Docker 命名卷 `ndi-java_minio-data`；停启容器不会清除数据。若项目目录名称或 Compose 项目名改变，命名卷前缀会随之变化。

本地管理员账号由 `docker/compose.yml` 中的 `MINIO_ROOT_USER` 与 `MINIO_ROOT_PASSWORD` 配置。上线 CentOS 7 前必须改为由环境变量、部署平台密钥或其他受控方式注入，不能沿用本地开发密码。
