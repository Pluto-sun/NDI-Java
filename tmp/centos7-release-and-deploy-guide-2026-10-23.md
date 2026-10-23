# CentOS 7 前后端打包部署操作说明

> 本文保留最初 HTTPS 交付方案与测试记录。后续已增加并默认采用 IP + HTTP 模式，无证书场景请以 [IP 与 HTTP 部署说明](ip-http-server-readiness-and-deployment-2026-10-23.md) 为准；本页下述域名/证书前置条件仅适用于 HTTPS 模式。

## 交付范围与前置条件

面向本项目维护人员，现已提供本地打包脚本和服务器部署入口。默认拓扑为 Java、Nginx、MinIO 原生进程同机，前端部署在应用域名根路径，对象存储使用另一 HTTPS 域名。服务器不需要 Node.js、npm、Maven 或 Docker。异机、容器、域名子路径或纯 IP/HTTP 访问不能直接套用本模板。

服务器需具备 Java 21、MySQL 8、MinIO、Nginx、systemd、Bash 4、curl、sha256sum、flock、ss 等工具，Nginx 在 `http` 中 include `/etc/nginx/conf.d/*.conf`。脚本不自动安装软件，也不改防火墙或禁用 SELinux。CentOS 7 已于 2024-06-30 结束维护，兼容脚本不消除系统安全维护风险。[CentOS 官方说明](https://www.centos.org/centos-linux/)

首次部署还需在服务器本地准备以下信息，无需将密码发送给 AI：

| 信息 | 配置位置 |
| --- | --- |
| 应用域名、MinIO 对象域名，两者不同并指向 Nginx | `/etc/ndi/deploy.conf` |
| 两个域名对应的证书链及私钥绝对路径，证书须被浏览器和服务器 curl 信任 | 同上 |
| Java 21 实际路径、Java 监听端口、MinIO 内网上游地址 | 同上 |
| 已备份数据库或确认目标为空库 | `DATABASE_BACKUP_CONFIRMED=yes` |
| 专用数据库 URL、账号和密码，具备 Flyway 迁移所需权限 | `/etc/ndi/application.env` |
| 预创建私有 Bucket、应用专用访问密钥、准确的 MinIO 内外网地址 | 同上 |
| 新空库首次管理员账号及密码；已有管理员时不覆盖 | 同上 |
| 精确的前端 HTTPS Origin 对应的 MinIO Bucket CORS | MinIO 管理配置 |

## 本地打包

在 NDI-Java 根目录执行：

```bash
bash scripts/package-release.sh
# 完整后端集成验证需本机 Docker；推荐正式交付前使用：
bash scripts/package-release.sh --verify
```

默认运行前端格式检查与构建、76 项后端单元/架构测试；`--verify` 再运行后端集成测试。脚本不自动运行浏览器测试，实际检查范围写入 `release.txt`。构建在 `tmp/ndi-package.*` 独立副本进行，不清理 IDEA 的 `target`；成功后清理临时副本，失败时保留诊断目录且不出包。

产物位于 `dist/releases/ndi-release-时间戳-提交号.tar.gz`，包含后端 JAR、前端静态文件、部署入口、模板、版本记录及 SHA-256 清单。发布 JAR 强制重建并排除 `application-local.yml`，不包含本地连接密码、原始 Excel 或服务器真实凭据。用户仍手动上传，脚本不访问新服务器。

## 服务器首次操作

将压缩包上传到专用交付目录，解压并进入其目录，再执行：

```bash
sudo bash deploy.sh --init-config
sudoedit /etc/ndi/deploy.conf
sudoedit /etc/ndi/application.env
sudo bash deploy.sh --check
sudo bash deploy.sh --deploy
```

`--init-config` 只创建缺失配置，已有配置不覆盖。目录 `/etc/ndi` 为 root 所有、权限 `0700`，凭据文件为 `0600`。`deploy.conf` 是 root 维护的受信任 Bash 配置；`application.env` 使用 systemd 的 `KEY=value` 格式，不加 export、不使用变量替换，也不会被脚本当 shell 执行。通过 `readlink -f "$(command -v java)"` 确认真实 Java 路径后填写 `JAVA_BIN`。

脚本生成 `/etc/nginx/conf.d/ndi.conf`，其中应用站点托管 React、代理 `/api/`，对象站点将根路径原样代理到 MinIO S3 API，不转发至控制台。原有 Nginx 无须事先手工创建这两个站点，但 DNS、证书、私有 Bucket 和应用凭据必须先准备。MinIO 控制台继续只供管理员访问。[Nginx 代理 URI 规则](https://nginx.org/en/docs/http/ngx_http_proxy_module.html#proxy_pass)

Bucket CORS 示例随包提供为 `minio-cors.xml.example`，需把 Origin 改成实际应用域名。若维护人员已安装并配置 MinIO 管理工具 mc，使用 `mc cors set 服务别名/桶名 minio-cors.xml.example` 应用；运行部署不依赖 mc。Java 凭据不要使用 MinIO root，Bucket 不开放匿名读写。

SELinux 为 Enforcing 时，管理员需先确认适合本机的策略，例如允许 Nginx 代理连接，并为 `/opt/ndi` 的无秘密程序/静态文件配置持久 `httpd_sys_content_t` 标签。示例命令为 `sudo setsebool -P httpd_can_network_connect on`、`sudo semanage fcontext -a -t httpd_sys_content_t '/opt/ndi(/.*)?'`；已有规则需核对后用 `-m`，不能盲目重复新增。脚本检查策略并对自有发布目录执行 restorecon，不自动关闭 SELinux；证书文件的标签也需管理员核对。凭据仍留在不可由 Nginx 读取的 `/etc/ndi`。

## 更新、失败与验收

后续上传新包后执行 `--check`、`--deploy` 即可，保留原配置。脚本取得部署锁，检查校验清单、Java、配置、端口、MinIO readiness 和 Nginx 模板。发现其他 Java 进程或非托管 `ndi.service`、NDI 站点文件时拒绝覆盖或强杀，须维护人员先完成一次性接管。配置错误或端口冲突不会停止旧服务。

新版本放入 `/opt/ndi/releases/版本/`。先优雅停止 `ndi.service`，切换后端、启动并等待健康；通过后切换前端并重新加载 Nginx，不重启 MySQL、MinIO。已成功发布的同一个包允许再次部署并重启本应用，但必须确认原版本内容完全相同，不覆盖运行 JAR。失败版本保留现场，新包应使用新版本号；停止超时不强杀，不自动进行数据库或 JAR 回滚。

前端历史哈希资源保留在 `/opt/ndi/shared/assets`，防止旧页面懒加载 404；暂不自动删除历史版本或共享资源，维护人员需规划磁盘清理与备份。程序配置、日志、数据目录独立，数据库迁移不能靠切换旧 JAR 撤销。单实例更新有 API 暂停窗口，不是零停机。

部署末尾检查真实证书、首页、入口资源、详情深链接、SESSION 模式、未登录业务 API 的 401，以及 MinIO HTTPS readiness。随后仍需从用户网络实测登录、Excel 导入、附件上传封存及下载、地图。脚本不能仅靠 readiness 判断 Bucket 权限或 CORS 正确。地图还需要 Java 能访问既有外部边界服务。

运行日志通过 `sudo journalctl -u ndi -n 100 --no-pager` 查看。Nginx NDI 访问日志不记录查询字符串，日志权限限制为 `0640`；错误日志仍可能含敏感请求信息，不能公开。脚本不修改其他站点；仅自有站点配置发生应用时 reload Nginx。

## 已执行的本地验证与边界

后端完整验证阶段通过 76 项单元/架构测试及 122 项集成测试；发布 Profile 强制重建归档后，真实打包通过，并检查 JAR 不包含 `application-local.yml`。曾发现隔离副本漏带恢复测试入口、临时目录测试类丢失及 Maven 复用旧 JAR，现已分别补齐依赖、使用受控构建目录与完成后清理、启用强制创建；失败均未交付不合格包。

部署控制流程在一次性 Linux 及 CentOS 7 容器通过，包括配置初始化幂等、占位值拒绝、部署锁、文件损坏、额外文件、未托管服务、端口冲突、MinIO 未就绪、首次部署、同包再部署、运行中更新及健康失败时前端不切换。测试以替身模拟 systemctl、Java 和服务接口，未宣称已在目标服务器完成真实 systemd 重启。CentOS 7 的 systemd 219 管理器校验受 Docker cgroup 环境限制，必须由目标服务器的 `--check` 再确认。

另以真实 Nginx 及临时测试证书验证 HTTPS、首页、深链接、JS、缺失资源 404、API 前缀/Host/协议、401 保留、Actuator 屏蔽、对象路径和签名查询参数保留、不含查询字符串的访问日志。真实发布 JAR 在隔离 MySQL 及 MinIO 依赖下，以 prod 完成空库迁移、首次管理员、CSRF、会话登录和开发身份禁用验收；该测试的 HTTP Cookie 例外只影响本地验收进程，不改变 HTTPS 部署模板。浏览器业务回归未由打包脚本执行，已在发布记录明确标注。

相关测试入口为 `scripts/test-deploy-container.sh`、`scripts/test-nginx-container.sh`、`scripts/test-release-smoke.sh`。前两个只能在显式设置 `NDI_DEPLOY_TEST_CONTAINER=1` 的一次性容器运行，分别挂载 deploy 为 `/source`、测试脚本为容器入口；最后一个接收本地生成的压缩包路径，使用隔离 Compose 依赖，保留诊断日志并自动清理验收容器。素材依据为当前构建、认证、存储代码及已执行测试；知识库未匹配本项目具体部署流程，未引入其他架构组件。
