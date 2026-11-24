# HTTP Reading - 智能阅读平台

> 一个基于 Java Spring Boot 的阅读与问答平台，围绕“书籍管理、阅读进度、全文搜索、RAG 问答、记忆系统、MCP 工具接入”逐步演进。

---

## 项目简介

这个项目的核心目标，是把“读书”从单纯的内容展示，升级成一个可以检索、可以追问、可以记忆上下文、可以接入外部工具的智能阅读系统。

当前项目已经覆盖了以下方向：

- 用户注册、登录与身份认证
- 书籍浏览、章节查看、阅读进度记录
- 书架收藏与个人阅读状态管理
- 章节内容问答与检索增强回答
- 文档导入、切片、向量化与检索
- 会话记忆、上下文构建与提示词拼装
- MCP 工具暴露与外部 MCP 服务调用
- Redis 缓存、消息队列、日志与基础监控能力

---

## 当前能力

### 1. 账号与访问控制

- 支持用户注册、登录与 JWT 无状态认证
- 通过安全过滤链保护需要登录的接口
- 对异常登录、无权限访问等场景做统一处理

### 2. 书籍与阅读

- 支持书籍列表、详情、章节列表与章节内容查看
- 支持书架收藏，且对重复收藏做幂等处理
- 支持阅读进度记录与恢复，方便用户继续阅读
- 静态阅读页面可直接访问，用于快速查看内容

### 3. 检索与问答

- 支持对书籍和章节内容进行全文检索
- 支持将文档拆分为片段后做向量检索
- 支持基于检索结果生成答案，提高回答与原文的相关性
- 支持返回引用来源，方便用户追溯答案依据

### 4. 记忆与上下文

- 支持保存阅读问答过程中的关键记忆
- 支持按用户和会话维度检索历史记忆
- 支持把历史对话、外部输入片段和系统提示拼装成统一上下文
- 支持上下文压缩与长度控制，适配后续模型调用

### 5. MCP 接入

- 项目本身可以作为 MCP Server，对外提供标准化工具能力
- 项目也可以作为 MCP Client，按配置调用外部 MCP 服务
- 支持工具白名单、超时设置、调用路由与结果解析
- 支持在候选目标不明确时，让用户二次确认后继续执行

### 6. 数据与基础设施

- MySQL 负责核心业务数据存储
- Redis 用于缓存和部分状态管理
- Elasticsearch、RabbitMQ、Qdrant 等组件已纳入配置体系
- 支持日志分级、结构化日志输出和本地调试

---

## 模块划分

### 接口层

负责接收 HTTP 请求，向外提供用户、书籍、阅读、问答和 MCP 相关接口。

### 业务层

负责处理书籍、章节、书架、阅读进度、检索、记忆、问答等核心流程。

### 检索层

负责文档解析、切片、向量化、召回和相关内容排序。

### 上下文层

负责整理会话历史、记忆片段和用户输入，生成适合模型使用的上下文。

### MCP 层

负责工具注册、外部服务选择、工具调用控制、确认交互与调用结果封装。

### 基础设施层

负责数据库、缓存、消息队列、日志、配置和外部服务连接。

---

## 主要技术

| 类别 | 技术 |
|------|------|
| 框架 | Spring Boot 3.3.4（Java 17） |
| 持久层 | Spring Data JPA + MySQL |
| 缓存 | Spring Data Redis |
| 安全 | Spring Security + JWT |
| 检索 | Elasticsearch + 向量检索 |
| 消息 | RabbitMQ |
| AI | 外部大模型 API、RAG、MCP 工具调用 |
| 构建 | Maven |
| 部署 | Docker / Docker Compose |

---

## 配置说明

直接运行 Java 时，可将
[src/main/resources/application.yml.example](src/main/resources/application.yml.example)
复制为 `src/main/resources/application.yml`。模板中的数据库、Redis、Elasticsearch、
RabbitMQ、Qdrant 和模型配置均支持环境变量覆盖。

使用 Docker Compose 时复制 [.env.example](.env.example) 为 `.env`，真实密码和
API Key 只保存在被 Git 忽略的 `.env` 中。

配置主要包含以下内容：

- 服务器端口、文件上传限制、JWT 密钥与有效期
- MySQL、Redis、Elasticsearch、RabbitMQ 连接信息
- 缓存开关
- 本地存储目录
- MCP 客户端列表与工具白名单
- 向量数据库连接参数
- 模型 API Key 与嵌入模型配置

---

## 本地运行

### 方式一：直接启动

```bash
mvn spring-boot:run
```

### 方式二：打包后启动

```bash
mvn clean package
java -jar target/*.jar
```

### 方式三：Docker Compose（推荐）

```bash
cp .env.example .env
# 编辑 .env，替换所有 change-me 值
docker compose up --build -d
docker compose ps
docker compose logs -f app
```

Compose 会启动应用、MySQL、Redis、Elasticsearch、RabbitMQ 和 Qdrant，并使用
具名卷保存数据。Elasticsearch 镜像会安装匹配版本的 IK 中文分词插件，首次构建需
联网下载。应用默认访问 `http://127.0.0.1:8080`；基础设施端口默认只绑定
`127.0.0.1`。停止服务但保留数据使用 `docker compose down`，不要在需要保留数据
时添加 `-v`。

`.env` 中常用连接变量：

| 组件 | 主要变量 | 默认宿主机端口 |
| --- | --- | --- |
| MySQL | `MYSQL_DATABASE`、`MYSQL_USER`、`MYSQL_PASSWORD` | 3306 |
| Redis | `REDIS_PASSWORD` | 6379 |
| Elasticsearch | `ELASTICSEARCH_VERSION`、`ELASTICSEARCH_IK_VERSION`、`ELASTICSEARCH_JAVA_OPTS` | 9200 |
| RabbitMQ | `RABBITMQ_USERNAME`、`RABBITMQ_PASSWORD` | 5672 / 15672 |
| Qdrant | `QDRANT_API_KEY` | 6333 |
| JWT | `SECURITY_JWT_SECRET`、`SECURITY_JWT_TTL_SECONDS` | 无 |

如需让其他设备访问应用，只修改 `APP_BIND_ADDRESS`；不建议把
`INFRA_BIND_ADDRESS` 改为 `0.0.0.0`。

从 v0.0.3 升级到 v0.0.4 时，已有 `.env` 还需补充 `SECURITY_JWT_SECRET`；可用
`openssl rand -hex 32` 生成。不要复用数据库密码，也不要将 `.env` 提交到 Git。

#### 在新电脑上首次启动

新电脑需要先安装 Git 和 Docker Desktop，并确认 Docker Desktop 已启动。代码应先
从原电脑推送到远程仓库，然后在新电脑建立 `Noesis` 项目总目录并克隆：

```bash
mkdir -p Noesis
cd Noesis
git clone <reading_assistant 仓库地址> reading_assistant
git clone <readest 仓库地址> readest
cd reading_assistant
cp .env.example .env
```

使用任意文本编辑器打开 `.env`，将下列配置中的 `change-me` 占位值替换为不同的
随机密码或密钥：

```dotenv
MYSQL_PASSWORD=<随机密码>
MYSQL_ROOT_PASSWORD=<随机密码>
REDIS_PASSWORD=<随机密码>
RABBITMQ_PASSWORD=<随机密码>
QDRANT_API_KEY=<随机密钥>
SECURITY_JWT_SECRET=<至少 32 字符的随机密钥>
```

可重复执行 `openssl rand -hex 32` 生成随机值。JWT 密钥变更后，已登录客户端需要
重新登录；模型 API Key 暂时不用时可以留空。
完成配置后首次构建并启动：

```bash
docker compose up --build -d
docker compose ps
docker compose logs -f app
```

服务启动后访问 `http://127.0.0.1:8080/reader.html`。以后再次启动不需要重新创建
`.env`，在项目目录执行 `docker compose up -d` 即可；停止并保留数据使用
`docker compose down`。

`.env`、MySQL 数据、Elasticsearch 索引、Qdrant 数据和已存书籍不会随 Git 迁移。
需要保留旧电脑数据时，应单独备份和恢复；不要使用 `docker compose down -v`，该
命令会删除当前电脑上的项目数据卷。

### 智能增量导入书籍

将 EPUB 文件统一命名为：

```text
书名__作者__状态.epub
乡土中国__费孝通__完结.epub
某书__张三__连载中.epub
```

然后执行：

```bash
bash scripts/import-books-batch.sh \
  --dir /home/hamster/books \
  --filename-metadata
```

脚本会自动读取书名、作者和状态。首次运行会为历史书籍建立 SHA-256
索引；之后通过目录中的 `.http-reading-import-state.tsv` 跳过未变化的文件，
相同内容即使改名也不会重复导入。

如果需要用更好的源文件覆盖现有书籍并保留 bookId、书架和阅读进度：

```bash
curl -X POST http://localhost:8080/api/admin/books/51/replace \
  -F "file=@/home/hamster/books/学会提问__作者__完结.epub"
```

替换成功后可按需重新构建该书的 RAG 索引。

---

## 版本规划

当前集成版本见 [VERSION](VERSION)。

### 客户端原文件下载

登录后可通过 `GET /api/client/books/{bookId}/file` 获取已导入书籍的 EPUB/PDF
原文件。接口支持 `ETag`、`If-None-Match`、单段 `Range` 和 `If-Range`，供桌面
客户端断点下载与本地缓存；文件访问被限制在对应书籍的 `source` 目录中。

---

## AI Agent Testing

项目包含离线 mock 回归、Planner/ToolPlanner live 测试和使用 fake MCP 结果的 shadow live 测试。

---

## 项目结构

```text
Noesis/
├── readest/                 # 跨平台阅读器客户端
└── reading_assistant/       # 智能阅读与 AI 助读后端
    ├── pom.xml
    ├── docker-compose.yml
    ├── Dockerfile
    ├── README.md
    ├── src/main/
    │   ├── resources/
    │   │   ├── application.yml
    │   │   ├── application.yml.example
    │   │   └── static/
    │   │       └── reader.html
    │   └── java/com/example/httpreading/
    │       ├── controller/
    │       ├── service/
    │       ├── context/
    │       ├── memory/
    │       ├── mcp/
    │       ├── mq/
    │       ├── repository/
    │       ├── security/
    │       ├── config/
    │       ├── domain/
    │       └── dto/
```
