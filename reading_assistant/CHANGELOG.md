# CHANGELOG — 智能阅读平台迭代记录

---

## v0.3.5：从历史引用跳回原文

提问时保存选区 CFI 和书籍文件指纹，历史引用支持跳回原文并返回跳转前的阅读位置。文件版本变化、定位失效或旧消息缺少 CFI 时回退到对应章节并提示；文件不可用时仍可查看原文快照。接口新增可选定位字段，兼容原有请求和历史。

验证结果、表结构变化及待验收项见 [版本记录](../readest/docs/reading-assistant/v0.3.5.md)。真实 macOS 与跨设备定位仍待实测。

---

## v0.3.4：流式显示与停止生成

快速与深度模式支持 SSE 输出，深度模式显示规划、资料收集及回答修订阶段。可停止生成并保存部分回答，断线后从会话历史恢复；停止、中断和失败的轮次不进入后续默认上下文。原同步问答接口保持兼容。

客户端助读专项 10 项、后端相关 58 项测试及 TypeScript、lint 通过；全量客户端测试结果和待验收事项见 [版本记录](../readest/docs/reading-assistant/v0.3.4.md)。真实模型测速与 macOS 联调待实测。

---

## v0.3.1–v0.3.3：连续助读与快速问答

按账号和书籍保存、切换 AI 会话，固定面板后自动更新划词引用，并支持共享会话历史的快速／深度模式。快速模式使用已有上下文，仅发起一次模型生成请求。

---

## v0.2.8：批量云端导入

Readest 支持同时选择多个 EPUB，逐本查重和入库并汇报结果。

---

## v0.2.7：云端导入接入 Readest 书库同步

使用标准书籍目录和书库登记流程，支持按需迁移旧云端路径。

---

## v0.2.6：账号笔记编辑与同步

助读面板可修改账号笔记，并在重新打开时读取服务端保存的状态。

---

## v0.2.5：云端书籍 AI 助读

按需构建云端书籍的章节索引，限制正文访问为书籍所有者。

---

## v0.2.4：云端导入与建档

增加 Readest 云端 EPUB 导入入口及幂等建档。

---

## v0.2.3：作品查重与导入预留

按规范化书目和文件哈希查重，新增认证导入预留接口。

---

## v0.2.2：书目信息规范化

增加 EPUB 元数据读取及后端书名、作者匹配键。

---

## v0.2.1：暂停自动检查更新

Readest 暂停书库及阅读页自动检查更新，保留「关于」页手动检查。

---

## v0.1.9：v0.1 端到端多用户验收与阶段总结

完成 v0.1「公共书库云存储、用户专属进度与 AI 助读系统」全链路回归验收：验证多用户原生
注册与登录切换、Google Drive 云端公共共享书库借阅、多用户专属 CFI 进度与划词笔记后端
强隔离、以及云端拉取书籍自动按书名/作者匹配 `bookId` 并启用 AI 助读问答。完成 v0.1 阶段
总结与版本归档。

---

## v0.1.8：Google 云端书籍与后端 AI 自动匹配建档及 XML EPUB 兼容

Readest 客户端打开经 Google Drive 同步的书籍时自动按书名与作者匹配并缓存后端远程
`bookId`，直接启用 AI 助读与个人进度/笔记同步；后端 `DocumentParseService` 支持解
析 `.xml` 章节 EPUB 样本。

---

## v0.1.7：个人 CFI 阅读进度与划词笔记收归后端账号

后端阅读进度（`/api/user/books/{bookId}/progress`）与划词批注
（`/api/user/books/{bookId}/annotations`）扩展 `cfi` 定位字段并按 `userId` 强隔
离；Readest 客户端阅读器自动拉取/上报当前登录账号的专属 CFI 进度与个人笔记。

---

## v0.1.6：书籍导入双路径目标选择（本地 vs Google 云端）

Readest 客户端支持在导入书籍时选择存储目标（`Local device` 本地私有或 `Google
Drive` 云端共享书库），选择云端时自动将书籍原文件上传至 `/Readest/books/<hash>/`。

---

## v0.1.5：客户端原生注册/登录切换与会话生命周期完善

Readest 客户端 Reading Assistant 集成表单新增原生注册功能，支持新用户在界面上直接
调用 `/api/auth/register` 注册账号并自动登录保存 JWT 会话。

---

## v0.1.4：Google OAuth 凭据配置与授权持久化

支持运行时与环境变量注入 Google Drive OAuth Client ID（含 `.env*.example` 模板与
空白字符清理），默认对齐 macOS / 移动端 Tauri 预置反向 DNS 回调与钥匙串刷新令牌
持久化，并支持 Web 端有效期内令牌跨标签页恢复。

---

## v0.1.3：解除 Google Drive 云同步会员门控

Readest 客户端解除第三方云同步的 Readest Cloud 付费订阅门控，未登录或免费状态下
可直接打开 Google Drive 集成配置页、行内切换同步开关并执行自动/手动同步。

---

## v0.1.2：建立 Noesis 项目总目录与双仓库归整

在 `Developer/` 下建立 `Noesis/` 项目总目录，将 `reading_assistant/` 与 `readest/`
迁入其中并列维护，保留各自独立的 Git 仓库、提交历史与 Docker 数据卷命名，并同步
更新新电脑克隆步骤、项目结构说明与双仓库版本记录。

---

## v0.1.1：Google Drive 云同步蓝图与目录基线

建立 [v0.1 阶段蓝图](docs/roadmap/v0.1.md) 与 `v0.1.1`–`v0.1.6` 小版本拆分，明确由
Readest 原生 Google Drive 同步引擎承担书库、原始书籍、CFI 阅读进度及高亮批注的跨
端同步，并在本阶段打通无付费门控直接可用、远程书籍 AI 映射协同与 XML 章节 EPUB
兼容。

---

## v0.0.6：Readest AI 助读接入

Readest 导入的远程书籍在本地记录后端书籍 ID，在阅读器 AI 面板中携带当前章节、
问题和划词调用已有 `/api/ai/chat`，显示回答及引用。后端接口保持原契约。

---

## v0.0.5：远程书库与原文件阅读

新增需登录的精简书库列表 `GET /api/client/books`，只返回可下载 EPUB/PDF 的书名、
作者、格式与 ID，不包含服务器文件路径。Readest 可分页浏览、下载并导入到本地书库，
之后使用自身阅读器打开缓存的文件。

---

## v0.0.4：Readest 登录与 JWT 生命周期

后端登录响应新增 JWT 到期时间，并提供受保护的会话校验接口；JWT 密钥、有效期和
客户端来源由环境变量配置。Readest 新增 Reading Assistant 连接页，在设备本地保存
服务地址和令牌，支持登录恢复、过期清理、服务端校验和退出，不改变 Readest Cloud
账号及既有客户端。

---

## v0.0.3：环境变量与 Docker 运行栈

MySQL、Redis、Elasticsearch、RabbitMQ、Qdrant 和应用连接信息统一改由环境变量
注入；新增可复制的 `.env.example`、完整 Compose 编排、数据卷和健康检查，并将
应用镜像改为多阶段构建。Elasticsearch 镜像内置匹配版本的 IK 中文分词插件；
基础设施端口默认只绑定本机。

---

## v0.0.2：受控原文件下载

新增需登录访问的 `GET /api/client/books/{bookId}/file`，仅从书籍隔离目录流式
提供 EPUB/PDF 原文件，并支持 ETag、条件请求和单段 Range 下载。补充路径安全、
下载服务、HTTP 行为与认证边界测试；现有网页、Flutter 和数据库结构不变。

---

## v0.0.1：规划与工程基线

从 v0.0.1 起，版本蓝图和精要变更记录分别维护在
[`docs/roadmap`](docs/roadmap) 与 [`docs/versions`](docs/versions)，当前版本见
[VERSION](VERSION)。本版本仅建立 Readest 客户端集成规划，不改变运行时行为。

---

## [2026-05-05] W7：RAG 书内问答 + W5 收尾

### 新增
- 新增 `EmbeddingService.java` — 文本向量化服务，封装 DashScope embedding 调用
- 新增 `ChunkDoc.java` — ES chunk 文档类（indexName: book_chunks），字段：bookId/chapterIndex/chunkIndex/bookTitle/chapterTitle/content/vector
- 新增 `ChunkRepository.java` — ES chunk 仓库接口
- 新增 `ChunkingService.java` — 章节切分服务（500字符/块，50字符重叠，滑动窗口）
- 新增 `RagService.java` — RAG 核心服务（向量检索 + prompt 构建 + Kimi 生成）
- 新增 `AiChatResponse.java` — AI 问答响应 DTO（answer + sources 引用列表）
- 新增 `ModelClient.embeddingByDashScope()` — DashScope embedding API 调用方法
- `AdminController.java` — 新增 `POST /api/admin/chunks/rebuild/{bookId}` 触发 chunk 索引重建

### 修改
- `AiChatService.java` — 原有直接 prompt 方式改为调用 RagService（检索 + 生成）
- `AiChatController.java` — 返回类型从 `String` 改为 `AiChatResponse`（含回答 + 引用来源）
- `ModelClient.java` — 新增 DashScope embedding 配置（`model.dashscope.apiKey` / `embeddingModel`）
- `application.yml` — 新增 DashScope 配置（apiKey、embeddingModel: text-embedding-v3）
- `SecurityConfig.java` — 新增 `/api/ai/**` 白名单放行

### 环境搭建
- 申请 DashScope（阿里云百炼）API Key，支持 `text-embedding-v3`（1024 维向量）
- 手动创建 ES `book_chunks` 索引 mapping（Spring Data ES 无法自动处理 `dense_vector` 字段）

### 遇到的问题及解决
- **Spring Data ES 无法处理 `List<Float>` 向量字段**：自动创建的 `book_chunks` 索引缺少 `vector` 字段 → 手动用 curl 创建 mapping，显式声明 `dense_vector` 类型
- **Kimi API Key 无 embedding 权限**：原 key 只有 chat 权限，embedding 返回 403 → 改用 DashScope（阿里云百炼）embedding 服务
- **ES 7.x Java API Client KNN 语法不兼容**：`knn { ... }` 语法报错 `Unknown key for a START_ARRAY` → 换用 `script_score` + `cosineSimilarity` 实现向量检索
- **ES `script_score` 查询维度不匹配**：curl 测试时向量维度 9 ≠ 1024 → 用完整 1024 维向量测试；Java 代码中改用 `JSONObject` 动态构造避免字符串拼接错误
- **ES `knn` 查询语法在 7.17 版本报错**：`Unknown key for a START_OBJECT in [knn]` → 改用 `script_score` + `cosineSimilarity(params.queryVector, 'vector') + 1.0` 替代方案
- **`/api/ai` 需要登录认证**：返回 401 → 在 SecurityConfig 中添加 `/api/ai/**` 白名单放行

### 技术方案
| 组件 | 实现 |
|------|------|
| 向量模型 | DashScope `text-embedding-v3`（1024 维） |
| 向量存储 | Elasticsearch `book_chunks` 索引（dense_vector） |
| 向量检索 | ES `script_score` + `cosineSimilarity`（兼容 ES 7.x） |
| chunk 切分 | 500 字符/块，50 字符重叠滑动窗口 |
| 生成模型 | Kimi（moonshot-v1-8k），复用现有 ModelClient |
| 检索数量 | 默认 top-3 相关片段 |

### 简历素材
> 基于书籍章节文本切分（500字符块/50字符重叠）构建 RAG 检索链路，接入 DashScope embedding 服务实现文本向量化，通过 Elasticsearch script_score + cosineSimilarity 实现向量相似度检索，再将检索片段与问题拼接后交由 Kimi 生成回答，并返回引用来源以提升回答可解释性。

---

## [2026-05-03] W4：Elasticsearch 全文搜索

### 新增
- 新增 `BooksDoc.java` — ES 文档类（indexName: books_doc），字段：id、title、author、intro、status、chapterTitles、chapterContents
- 新增 `BooksDocRepository.java` — ES 仓库接口，继承 `ElasticsearchRepository`
- 新增 `SearchService.java` — 搜索服务，多字段匹配（title^3/author^2/intro/chapterTitles^2/chapterContents）+ 分类过滤（status）+ 高亮（\<em\>标签）+ 分页
- 新增 `SearchController.java` — `/api/search` 搜索接口（GET，参数：keyword/category/page/pageSize）
- 新增 `AdminController.java` — `/api/admin/sync/{bookId}` 单本同步 + `/api/admin/sync/all` 批量同步
- 新增 `BooksSearchResult.java` — 搜索结果 DTO（包含 book + highlights）

### 修改
- `DataInitializer.java` — 初始化书籍时自动同步到 ES（searchService.indexBook）
- `BooksService.java` — 新增 saveBook()（MySQL + ES 同步）、syncAllBooksToEs()（批量同步）
- `SearchService.indexBook()` — 删除 createdAt/updatedAt 字段（避免 LocalDateTime 序列化问题）
- `SecurityConfig.java` — 新增 `/api/admin/**` 和 `/api/search/**` 白名单放行

### 环境搭建
- Docker 安装 ES 7.17.20（`docker run -d --name elasticsearch -p 9200:9200 elasticsearch:7.17.20`）
- 安装 IK 中文分词器（`docker exec elasticsearch bin/elasticsearch-plugin install --batch https://get.infini.cloud/elasticsearch/analysis-ik/7.17.20`）
- IK 分词验证：`curl http://localhost:9200/_analyze -H "Content-Type: application/json" -d "{\"text\":\"Java编程思想\",\"analyzer\":\"ik_max_word\"}"`

### 遇到的问题及解决
- **ES 索引名大小写问题**：`booksDoc` 报错 "Invalid index name [booksDoc], must be lowercase" → 改成 `books_doc`
- **Security 白名单问题**：`/api/admin/**` 和 `/api/search/**` 未放行导致 401 → 在 SecurityConfig 加 `.requestMatchers()`
- **SearchService 编译错误**：删了 BooksDoc 的 createdAt/updatedAt 字段后，indexBook() 里的 setter 调用还在 → 删掉对应两行
- **BooksServiceTest 构造器参数不匹配**：新增 SearchService/ChaptersService 后测试用例构造器报错 → 加 @Mock + 更新 setUp()
- **日期转换错误（未解决）**：ES 存 LocalDateTime 序列化问题，方案是去掉日期字段

### 待做
- [ ] W4：修改前端 reader.html，将 `/api/books?keyword=` 改为 `/api/search?keyword=`，并渲染高亮（\<em\>标签显示为橙色）
- [ ] W4：前端分类过滤对接 ES 搜索的 category 参数
- [ ] W5：接入 RabbitMQ，书籍新增/更新改为异步同步到 ES（主链路更快）

### 简历素材
> 引入 Elasticsearch 构建书籍与章节全文索引，实现关键词检索、高亮展示与相关性排序（title^3 > author^2 > chapterTitles^2 > chapterContents），提升搜索体验并降低数据库模糊查询压力。

---

## [2026-04-27] W2-1：日志规范

### 新增
- 新增 `RequestIdFilter.java` — 为每个请求生成唯一请求ID（req-xxx），贯穿整个请求生命周期
- 新增 `WebConfig.java` — 注册 RequestIdFilter，统一过滤所有请求

### 修改
- `application.yml` — 新增日志格式配置，包含请求ID `%X{reqId}`，日志输出到 `server.log`
- `BooksController` — 新增 6 处日志（查询列表、查询详情、章节列表、章节内容）
- `UserController` — 新增 4 处日志（注册尝试/成功、登录尝试/成功/失败）
- `ReadingController` — 新增 4 处日志（获取进度、更新进度）
- `BookshelfController` — 新增 4 处日志（获取书架、添加书籍）
- `AiChatController` — 新增 2 处日志（问答请求/完成）
- `JwtAuthFilter` — 新增 3 处日志（JWT认证成功/失败）
- `GlobalExceptionHandler` — 全局异常处理器新增详细日志（业务异常/参数异常/认证异常/系统异常）

### 日志规范
| 场景 | 级别 | 示例 |
|------|------|------|
| 正常业务流程 | INFO | `log.info("用户登录成功 - userId:{}")` |
| 潜在问题 | WARN | `log.warn("用户登录失败 - reason:{}")` |
| 异常/系统错误 | ERROR | `log.error("系统异常 - {}", e.getMessage(), e)` |
| 开发调试 | DEBUG | `log.debug("方法入参: {}")` |

### 日志格式
```
2026-04-27 00:30:00.123 [http-nio-8080-exec-1] INFO  [req-abc12345] BooksController - 查询书籍列表完成 - 返回 10 条
```

### W2完成清单
- ✅ 日志规范（请求ID串联、Controller日志、全局异常日志）

### 待做
- [ ] W2：单元测试、集成测试、模拟数据脚本

---

## [2026-04-25] W1收尾：排序/筛选 + Security认证处理器

### 新增
- 新增 `AuthenticationErrorHandler.java` — 未认证时返回 CommonResponse JSON 格式（401）
- 新增 `AccessDeniedErrorHandler.java` — 未授权时返回 CommonResponse JSON 格式（403）

### 修改
- BooksController：新增 `category`、`sortBy`、`order` 参数，支持分类筛选和排序
- BooksService：新增 Sort 逻辑，支持按 updatedAt/createdAt/title/author/status 排序
- BooksRepository：新增 `findByStatus(status, pageable)` 方法
- ErrorCode：新增 `toException()` 方法（返回异常对象，用于 orElseThrow 场景）
- ChaptersService：修复 `orElseThrow` 编译错误，改为 `toException()`
- SecurityConfig：注册 AuthenticationErrorHandler + AccessDeniedHandler

### 问题修复
- 修复 ChaptersService 编译错误：`.throwException()` 改为 `.toException()`
- 修复 sortBy 默认值拼写错误：`updateAt` → `updatedAt`
- 解决 MySQL 建表问题：application.yml 新增 `spring.jpa.hibernate.ddl-auto: update`
- 解决分类筛选为空：数据库 status 字段中文乱码，UPDATE 修正为正确中文值
- 解决 Hibernate Dialect 检测失败：显式指定 `spring.jpa.database-platform: MySQLDialect`

### W1完成清单
- ✅ Swagger 接入
- ✅ 参数校验（@Min/@Max/@Positive/@NotNull）
- ✅ 统一错误码（ErrorCode + BusinessException）
- ✅ 分页 + 排序 + 筛选
- ✅ Security 自定义认证处理器（统一 401/403 JSON 格式）

### 待做
- [ ] W2：日志规范、单元测试、模拟数据脚本

---

## [2026-04-20] W1：统一错误码

### 新增
- 新增 `ErrorCode.java` — 13个错误码枚举（40001~50001）
- 新增 `BusinessException.java` — 业务异常，支持 `throwException()`
- `CommonResponse` 新增 `error(ErrorCode)` / `error(ErrorCode, String)` 工厂方法

### 修改（Service层5处）
- UserService: `IllegalArgumentException` → `ErrorCode.DUPLICATE_USERNAME`
- ChaptersService: → `ErrorCode.CHAPTER_NOT_FOUND` / `INTERNAL_ERROR`
- AiChatService: → `BusinessException` / `ErrorCode.CHAPTER_NOT_FOUND`

### 重要发现（今日）
- GlobalExceptionHandler 里的 `AuthenticationException` / `AccessDeniedException` **永远不会被触发**
- 原因：Spring Security 的 `ExceptionTranslationFilter` 先于 `@ControllerAdvice` 拦截这两个异常，直接由 Security 内置 handler 处理
- JwtAuthFilter 同时塞了两份 userId：`request.setAttribute`（实际用）+ `SecurityContextHolder`（存了没用）
- SecurityConfig 目前 `/api/user/**` 需要认证，但无 token 时返回的是 Spring Security 默认格式，不是 CommonResponse

### 待做
- [ ] W1：分页 + 排序 + 筛选
- [ ] Security：自定义 AuthenticationEntryPoint / AccessDeniedHandler，统一 401/403 格式

---

## [2026-04-16] 项目升级计划启动

### 新增

- 新增 `8-WEEK-PLAN.md`，规划8周升级路线
- 确定项目最终定位：智能阅读平台（有搜索、有异步事件、有监控、有书内问答）

### 现状

- 项目为单体 Spring Boot 3.3.4 阅读平台
- 已具备：JWT认证、Redis缓存（穿透/击穿/雪崩）、统一返回体、全局异常、Docker部署
- 待升级：工程化、压测、ES搜索、RabbitMQ异步、监控、RAG+SSE

---

## 后续记录格式

每次迭代完成后，在此文件添加一条记录：

```
## [YYYY-MM-DD] 周次 + 主题

### 新增功能
- ...

### 修改优化
- ...

### 简历素材
> 一句完整的话，可直接写进简历
```
