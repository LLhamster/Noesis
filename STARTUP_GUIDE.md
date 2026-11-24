# Noesis 双项目启动与日常使用指南

本文整理了 `Noesis` 工作区下后端 [`reading_assistant/`](reading_assistant/README.md) 与跨平台阅读客户端 [`readest/`](readest/README.md) 的本地启动、客户端打包及日常使用流程。

## 一、启动后端 `reading_assistant` 并导入书籍

### 1. 检查环境变量配置（仅首次启动需要）

进入 `Noesis/reading_assistant` 目录，确认 `.env` 已配置完成（如尚未创建，可从 `.env.example` 复制）：

```bash
cd reading_assistant
cp -n .env.example .env
```

确认 `.env` 中的以下配置项已填写：
- **中间件密码**：`MYSQL_PASSWORD`、`MYSQL_ROOT_PASSWORD`、`REDIS_PASSWORD`、`RABBITMQ_PASSWORD`、`QDRANT_API_KEY`。
- **JWT 密钥**：`SECURITY_JWT_SECRET`（至少 32 字符随机字符串）。
- **模型配置**：`MODEL_API_KEY`（生成问答模型 API Key）与 `EMBEDDING_API_KEY`（DashScope 向量模型 API Key）。
- **绑定地址**：本机使用保持 `APP_BIND_ADDRESS` 为 `127.0.0.1`；若手机或平板需要在局域网内连接 Mac 后端，改为 `0.0.0.0`。

### 2. 启动后端 Docker 服务栈

确保 Docker Desktop 已运行，然后执行：

```bash
cd reading_assistant
docker compose up --build -d
docker compose ps
```

- **默认服务端口**：本机 `127.0.0.1:8080`（可通过 `docker compose logs -f app` 查看实时日志）。
- **日常停止服务（保留全部数据库与书籍数据）**：
  ```bash
  docker compose down
  ```
  *注意：保留数据时切勿添加 `-v` 参数，否则会删除 Docker 数据卷。*

### 3. 批量导入 EPUB 书籍并构建 RAG 向量索引

将 EPUB 文件按 `书名__作者__状态.epub`（例如 `乡土中国__费孝通__完结.epub`）放入本地书籍目录后，运行增量导入脚本：

```bash
cd reading_assistant
bash scripts/import-books-batch.sh \
  --dir ./books \
  --filename-metadata \
  --rebuild-rag
```

## 二、启动或打包客户端 `readest`

首次克隆后，从 Noesis 根目录初始化客户端依赖，并准备客户端环境文件。

```bash
git submodule update --init --recursive
cp -n readest/apps/readest-app/.env.tauri.example readest/apps/readest-app/.env.tauri
cp -n readest/apps/readest-app/.env.web.example readest/apps/readest-app/.env.web
```

根据你的日常使用方式，在 `Noesis/readest` 下选择对应命令：

| 使用场景 | 所在目录 | 执行命令 | 产物或访问入口 |
| :--- | :--- | :--- | :--- |
| **macOS 桌面端（开发调试）** | `Noesis/readest` | `pnpm tauri dev` | 直接弹出 macOS 桌面客户端窗口并支持热更新 |
| **macOS 桌面端（打包 `.app`）** | `Noesis/readest/apps/readest-app` | `pnpm dev-macos` | 自动打包并打开 `Noesis/readest/target/release/bundle/macos/Readest.app`（可拖入 `/Applications` 长期使用） |
| **iOS 模拟器调试** | `Noesis/readest/apps/readest-app` | `pnpm dev-ios-sim` | 编译并安装到当前启动的 iOS 模拟器 |
| **iOS 真机打包安装** | `Noesis/readest/apps/readest-app` | `pnpm dev-ios` | 编译 IPA 并安装到连接的 iOS 设备 |
| **Android 真机打包安装** | `Noesis/readest/apps/readest-app` | `pnpm dev-android` | 编译 APK 并安装到连接的 Android 设备 |
| **Web 浏览器端调试** | `Noesis/readest` | `pnpm dev-web` | 浏览器访问本机 `localhost:3000` |

## 三、在客户端中配置与日常正常使用

### 1. 连接 `reading_assistant` 书库并下载远程书籍

1. 打开 Readest 并停留在**书库（Library）**主界面。
2. 点击右上角设置图标 → **Integrations（集成）** → 在 **Content Sources（内容来源）** 下点击 **Reading Assistant**。
3. 填写后端连接信息：
   - **Server URL**：本机填写 `127.0.0.1:8080`（带 `http` 前缀；移动端填写 Mac 的局域网 IP 与 `8080` 端口）。
   - **Username / Password**：输入账号密码，首次使用可直接点击 **Register（注册）**，已有账号点击 **Sign In（登录）**。
4. 登录成功后，下方列表会展示后端已导入的 EPUB/PDF 书籍，点击右侧 **Import** 即可下载原文件并加入本地书架。

### 2. 开启 Google Drive 云同步（已解除会员门控，直接可用）

1. 打开右上角设置 → **Integrations（集成）** → 在 **Cloud Sync（云同步）** 下点击 **Google Drive**（已移除付费订阅限制，无需登录 Readest Cloud 账号）。
2. 点击 **Connect** 按钮：
   - **macOS / iOS / Android 客户端**：会自动唤起系统浏览器完成 Google 账号授权，授权后通过 `com.googleusercontent.apps.*` 反向 DNS 回调自动返回客户端，并将刷新令牌安全保存在系统钥匙串（Keychain）中，重启应用无需重新登录。
3. 连接成功后，保持开启 **Upload Book Files（上传书籍原文件）**：
   - 书库索引（`/Readest/library.json`）、书籍原文件（`/Readest/books/<hash>/<书名>.epub`）、精确到句段的 **CFI 阅读进度**以及**高亮与笔记批注**（`/Readest/books/<hash>/config.json`）会在翻页、关闭阅读器或点击 **Sync now** 时自动双向同步。

### 3. 使用阅读器内 AI 助读（划词提问与章节问答）

1. 在书架中打开从 Reading Assistant 导入的书籍。
2. 在正文中**划词选中任意段落**，或打开右侧边栏（Notebook）切换到 **AI** 标签页。
3. 输入问题并发送，客户端会携带当前书籍 ID、章节序号、划词文本与问题调用后端 `/api/ai/chat`，返回结合原书 RAG 检索与上下文记忆的回答及原文引用出处。
