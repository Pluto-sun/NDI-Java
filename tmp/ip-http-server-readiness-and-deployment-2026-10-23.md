# IP 与 HTTP 部署、数据库配置及空 MinIO 准备

## 当前结论

已按无域名、无证书场景补充 HTTP 部署模板，默认应用入口为 `http://服务器IP:8088`，附件入口为 `http://服务器IP:9002`。服务器不需要 npm 或 Maven。现有 Java 21、MySQL 8、Nginx 和 MinIO 是基础条件，但数据库账号权限、私有 Bucket、CORS 和客户端网络连通性尚需在服务器确认。本文替代旧部署文档中“仅支持域名与 HTTPS”的限制；HTTPS 模式仍保留。

HTTP 会明文传输账号、会话和附件，只应在受控网络使用。当前配置必须使用 `NDI_SECURE_COOKIE=false`，否则浏览器无法正常保留登录 Cookie；未来切换 HTTPS 时改为 true。

## 数据库在哪里修改

服务器运行配置在 `/etc/ndi/application.env`，不是源码中的 `application-local.yml`。解压交付包后，先执行 `sudo bash deploy.sh --init-config`，再用 `sudoedit /etc/ndi/application.env` 修改：

```properties
NDI_DATASOURCE_URL="jdbc:mysql://127.0.0.1:3306/ndi?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC"
NDI_DATASOURCE_USERNAME=ndi
NDI_DATASOURCE_PASSWORD=替换为服务器专用数据库密码
```

3306 是服务器示例端口，开发环境 Docker 的 3307 不应直接照搬。MySQL 已安装不代表 `ndi` 库和账号已存在：需管理员预先创建 UTF-8 数据库及仅对该库授权的账号。应用启动时 Flyway 会创建及升级业务表，因此账号需具备项目迁移涉及的建表、改表、索引和数据操作权限，不应使用 MySQL root。首次空库还需填写 `NDI_BOOTSTRAP_ADMIN_LOGIN` 和 `NDI_BOOTSTRAP_ADMIN_PASSWORD` 初始化管理员。

文件为 root 所有、0600，目录 `/etc/ndi` 为 0700。用 `KEY=value`，不写 export、不使用变量替换。特殊字符建议用双引号包裹，遵循 systemd EnvironmentFile 的转义规则；不要把真实凭据提交到仓库。更改后通过交付包重新执行 `--check`、`--deploy` 生效。

## HTTP 入口与应用配置

修改 `/etc/ndi/deploy.conf`：

```properties
DEPLOY_MODE=http
PUBLIC_IP=替换为浏览器能够访问的实际IPv4地址
WEB_PORT=8088
FILES_PORT=9002
JAVA_BIN=/usr/bin/java
APP_PORT=8080
MINIO_UPSTREAM=127.0.0.1:9000
START_TIMEOUT=180
DATABASE_BACKUP_CONFIRMED=yes
```

`DATABASE_BACKUP_CONFIRMED=yes` 仅在确认空库或已有可恢复备份后设置。若 java 不在示例路径，用 `readlink -f "$(command -v java)"` 获取实际路径。HTTP 模式忽略 APP_HOST、FILES_HOST 及证书项。已有配置不会被初始化脚本覆盖，需要手动加入上面的新配置。

`/etc/ndi/application.env` 同时设置：

```properties
SPRING_PROFILES_ACTIVE=prod
NDI_AUTH_MODE=SESSION
NDI_SECURE_COOKIE=false
NDI_MINIO_ENDPOINT=http://127.0.0.1:9000
NDI_MINIO_PUBLIC_ENDPOINT=http://替换为同一服务器IP:9002
NDI_MINIO_BUCKET=ndi-documents
NDI_MINIO_ACCESS_KEY=替换为应用专用密钥
NDI_MINIO_SECRET_KEY=替换为应用专用密钥密码
```

Nginx 会由部署脚本生成应用与对象两个站点，无需事先手写 MinIO 代理。对象站点必须直接代理 S3 API 根路径，保留 Host、端口和签名查询参数，不使用 `/minio/` 前缀，也不指向 9001 控制台。[Nginx 官方代理规则](https://nginx.org/en/docs/http/ngx_http_proxy_module.html#proxy_pass)

仅对用户网络放行 8088 和 9002；不要向公网开放 Java 8080、MySQL 3306、MinIO 内网 9000 或控制台 9001。已有服务如直接监听所有地址，应由管理员核对访问控制。SELinux Enforcing 时，需核对 `httpd_can_network_connect`、`/opt/ndi` 的 `httpd_sys_content_t` 持久标签，以及两个 Nginx 监听端口的 `http_port_t` 标签。脚本不关闭 SELinux、不自动改防火墙；端口已属于其他类型时不能盲目覆盖。

## 空 MinIO 的一次性准备

MinIO 是对象存储，不是关系型数据库；无需新建“数据库”，但必须先准备 Bucket。发布配置明确 `auto-create-bucket: false`，所以没有 Bucket 时应用附件功能不可用。

在服务器通过受保护的管理员会话配置好 `mc` 管理别名（以下以 `ndi-admin` 为例）。别名中使用管理员凭据，仅用于一次性维护；不要把 MinIO root 凭据写入应用。已配置别名后执行：

```bash
mc mb --ignore-existing ndi-admin/ndi-documents
mc anonymous set none ndi-admin/ndi-documents
mc anonymous get ndi-admin/ndi-documents
```

将交付包 `minio-cors.xml.example` 复制成维护用文件，将 AllowedOrigin 改成精确的应用 Origin，例如 `http://实际IP:8088`，不是 9002，也不带路径或末尾斜线。然后执行：

```bash
mc cors set ndi-admin/ndi-documents minio-cors.xml
mc cors get ndi-admin/ndi-documents
```

再通过与服务版本匹配的 MinIO 管理工具创建专用应用用户/访问密钥。随包提供 `minio-app-policy.json.example`，限定 `ndi-documents` 的对象读写、删除及必要列表操作，不授予创建 Bucket、管理用户或其他 Bucket 权限；桶名改变时同步修改两个 Resource。已创建的应用用户可关联该策略：

```bash
mc admin policy create ndi-admin ndi-app minio-app-policy.json.example
mc admin policy attach ndi-admin ndi-app --user 应用用户名
```

将其密钥写入 application.env。初始化命令是管理员主动操作，不由环境检测脚本执行。不同 MinIO/mc 版本的管理入口可能有差异，请先查看本机 `mc --version` 和对应命令 `--help`。CORS 仅放行跨域浏览器调用，不代表允许匿名访问；私有 Bucket 通过短时预签名链接上传下载。[MinIO 匿名策略](https://docs.min.io/aistor/reference/cli/mc-anonymous/)、[Bucket CORS](https://docs.min.io/aistor/reference/cli/mc-cors/mc-cors-set/)

## 先运行环境检测

只需上传仓库中的 `scripts/check-server-env.sh`，即可独立检测，无需先上传完整发布包：

```bash
sudo bash check-server-env.sh --public-ip 实际服务器IP
```

发布包内同一脚本名为 `check-env.sh`。它只读取环境、探测连接、执行可选只读 SQL，不安装软件、不创建数据库/Bucket、不停止服务，也不输出凭据。配置未初始化、没有 SQL 登录验证或未验证 Bucket 会明确报告 UNKNOWN，不能误认为全部部署就绪。

如果服务器已配置好 MySQL 的 login-path 和 MinIO 应用别名，可执行：

```bash
sudo bash check-env.sh --mysql-login-path ndi --minio-alias ndi-app
```

注意 sudo 使用 root 的登录配置及 mc 配置；应事先为执行账号正确配置。SQL 检查会强制连接 application.env 指定的主机、端口和库；login-path 应与应用账号一致，否则不能证明应用凭据有效。检查不执行 DDL，不能完整验证 Flyway 权限。mc 的应用别名只能证明该别名访问情况，不能替代真实附件写入验收；应用最小权限不含管理 CORS，读取策略失败时可以另用管理员别名检查，但仍要单独验证应用账号。

退出码：0 为所检查项目通过，1 为存在阻断失败，2 为仍有未验证项。WARN 包括 HTTP、CentOS 7 维护风险等。检测脚本不会从服务器自身证明外网防火墙/安全组可达；最终需要用户电脑实测两个入口。

## 打包与部署顺序

本地：`bash scripts/package-release.sh`，更完整的后端验证使用 `--verify`，产物在 `dist/releases/`。手动上传解压后，按顺序执行：

```bash
sudo bash deploy.sh --init-config
sudoedit /etc/ndi/deploy.conf
sudoedit /etc/ndi/application.env
# 完成专用数据库、MinIO Bucket/应用密钥/CORS、网络与 SELinux 准备后：
sudo bash check-env.sh
sudo bash deploy.sh --check
sudo bash deploy.sh --deploy
```

检测中的 UNKNOWN 需要按提示人工核对，不能直接忽略。交付包的 --check 校验文件、配置与候选服务定义，之后 --deploy 才受控停止旧 NDI 服务并部署；不按端口强杀其他应用，不重启 MySQL 或 MinIO。新后端失败时不切换新前端，不自动回滚数据库。

## 本轮验证范围

部署与环境检测流程在一次性 CentOS 7 容器内通过：包含 HTTP/HTTPS、重复部署、配置错误拦截、检测退出码 0/1/2、只读取配置而不执行内容、不修改配置或停止服务、不打印密码。systemd 与上游使用测试替身，不能代替目标服务器实际启动验证。

真实 Nginx 测试通过 HTTP 双端口、页面深链接、API、对象 Host/端口及签名查询保留，并回归 HTTPS 模板。前端修复 HTTP IP 页面缺少 SubtleCrypto/randomUUID 的兼容问题：摘要使用固定版本 @noble/hashes，UUID 仍使用浏览器安全随机数，不使用 Math.random；源码中已无对这两个受安全上下文限制 API 的直接依赖。

没有连接目标服务器，不能宣称该服务器已就绪。上线前仍需真实验证管理员登录、权限、Excel 导入、附件上传封存及下载，以及用户电脑访问对象地址。
