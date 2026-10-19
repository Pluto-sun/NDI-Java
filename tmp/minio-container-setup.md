# MinIO 容器安装与启动说明

## 镜像选择

MinIO 已撤下原先公开的 Docker Hub 与 Quay 预构建镜像，`minio/minio` 因此无法继续拉取。项目当前固定使用 `netiedge/minio:RELEASE.2025-01-20T14-49-07Z@sha256:e6c282005f6c8496256f8fd07e9a8b82073344c217ea87436d9af888db67eb0e`。该镜像由社区仓库保全，并声明保留对应上游版本的二进制和镜像层；摘要固定避免标签被替换。它不是 MinIO 官方当前发布渠道，不能以此替代生产环境的软件供应链治理。

容器镜像基线不是 CentOS 7 官方兼容性承诺。部署前必须在目标服务器验证 Docker Engine、内核、存储驱动、SELinux 与防火墙策略，并完成上传、下载和重启后对象仍可读取的验收。CentOS 7 已停止维护，正式上线前应制定操作系统升级计划；升级操作系统后，应同步评估更新的 MinIO 镜像版本。生产部署前应将已验收的摘要镜像同步至自有私有镜像仓库，并在部署清单中引用私有仓库地址。

项目的单节点 MinIO 用于当前附件开发与单实例部署。若生产环境需要多节点高可用、跨主机容灾或更高的可用性目标，应独立设计分布式对象存储部署与备份恢复方案，不能把本 Compose 文件直接作为集群方案。

## 操作命令

在项目根目录执行以下命令。第一次使用先拉取镜像：

```bash
./scripts/pull-minio-image.sh
```

等价的 Docker 命令如下：

```bash
docker pull netiedge/minio:RELEASE.2025-01-20T14-49-07Z@sha256:e6c282005f6c8496256f8fd07e9a8b82073344c217ea87436d9af888db67eb0e
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
