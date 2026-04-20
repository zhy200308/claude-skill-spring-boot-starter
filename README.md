# Claude Skill Spring Boot Starter

一个开箱即用的 Claude API Spring Boot Starter，将 **Skill 管理**、**多轮会话**、**文件对话**、**代理配置** 打包成 JAR，引入即用。

## 特性

- 🎯 **三种调用模式** — 单次执行 / 多轮会话 / 带 Skill 的会话，按需选择
- 📎 **Files API 支持** — 上传 PDF、图片后携带文件对话，多轮追问自动复用
- 🧩 **Skill 热插拔** — 上传 `.skill` 包即可注册新能力，支持运行时增删改
- 🌐 **代理零配置** — 自动识别系统代理（JVM 属性/环境变量），也支持自定义和强制直连
- 💾 **可扩展存储** — 默认内存 LRU，注入一个 `SessionStore` Bean 即可切 Redis / DB
- 📖 **SpringDoc 集成** — 自动注册 `claude-skill` 分组，Knife4j 面板直接可见

---

## 目录

- [快速开始](#快速开始)
- [配置参考](#配置参考)
- [代理策略](#代理策略)
- [REST API](#rest-api)
- [调用示例](#调用示例)
- [扩展指南](#扩展指南)
- [Skill 文件格式](#skill-文件格式)
- [FAQ](#faq)

---

## 快速开始

### 1. 安装到本地 Maven 仓库

```bash
cd claude-skill-spring-boot-starter
mvn clean install -DskipTests
```

### 2. 在你的项目中引入

```xml
<dependency>
    <groupId>com.claude.skill</groupId>
    <artifactId>claude-skill-spring-boot-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

### 3. 最少配置

```yaml
claude:
  api-key: sk-ant-xxxxxxxxxxxxxxxx
```

启动项目即可访问 `/claude/**` 下全部接口。

### 4. 三分钟体验

```bash
# ① 上传一个 skill
curl -X POST http://localhost:8080/claude/skills/upload \
  -F "file=@ai-rewrite.skill"

# ② 无会话单次调用
curl -X POST http://localhost:8080/claude/execute \
  -H "Content-Type: application/json" \
  -d '{"skillName":"ai-rewrite","text":"需要改写的文本"}'

# ③ 开启多轮对话
curl -X POST http://localhost:8080/claude/chat-with-skill \
  -H "Content-Type: application/json" \
  -d '{"skillName":"ai-rewrite","content":"请润色这段话..."}'
```

---

## 配置参考

```yaml
claude:
  # ── API ───────────────────────────────────────────────
  api-key: ${CLAUDE_API_KEY}           # 必填
  api-url: https://api.anthropic.com/v1/messages
  model: claude-sonnet-4-20250514
  anthropic-version: "2023-06-01"
  max-tokens: 4096
  timeout-seconds: 120

  # ── 代理 ──────────────────────────────────────────────
  proxy:
    mode: SYSTEM                       # SYSTEM(默认) | CUSTOM | NONE
    # CUSTOM 模式下才需配置：
    # host: 127.0.0.1
    # port: 7890
    # type: HTTP                       # HTTP | SOCKS5
    # username:
    # password:

  # ── Skill ─────────────────────────────────────────────
  skill:
    dir: skills                        # 相对路径基于 src/main/resources
    auto-load: true                    # 启动时自动扫描
    watch-dir: false                   # 目录热加载（实验性）

  # ── 会话 ──────────────────────────────────────────────
  session:
    type: MEMORY                       # MEMORY | CUSTOM
    max-size: 1000                     # LRU 上限
    ttl-minutes: 60                    # 0 = 不过期
    max-turns-per-session: 50

  # ── Web 端点 ──────────────────────────────────────────
  web:
    enabled: true                      # false 则不注册任何 REST 接口
    base-path: /claude
    springdoc:
      enabled: true                    # 跟随主项目 springdoc 开关
      group: claude-skill
      package-to-scan: com.claude.skill.web
```

### 配置细节

**SpringDoc / Knife4j 集成**
- 主项目启用 `springdoc.api-docs.enabled=true` 时，自动注册 `claude-skill` 分组
- 分组匹配 `claude.web.base-path` 下全部接口（默认 `/claude/**`）
- Knife4j 面板自动显示该分组，无需额外挂载

**Skill 目录解析**
- `claude.skill.dir` 为相对路径时，基于主项目 `src/main/resources` 解析
- 绝对路径时直接使用
- 目录不存在会自动创建
- 上传的 `.skill` 先持久化到此目录，再注册到内存

---

## 代理策略

`claude.proxy.mode` 三种模式：

| 模式 | 行为 |
|------|------|
| `SYSTEM`（默认） | 按下表优先级自动检测 |
| `CUSTOM` | 使用 `proxy.host` / `proxy.port` 配置 |
| `NONE` | 强制直连，忽略系统代理 |

**SYSTEM 模式检测顺序：**

| 优先级 | 方式 | 示例 |
|--------|------|------|
| 1 | JVM 启动参数 | `-Dhttps.proxyHost=127.0.0.1 -Dhttps.proxyPort=7890` |
| 2 | 环境变量 | `export HTTPS_PROXY=http://127.0.0.1:7890` |
| 3 | 系统默认 | 若都未设置则直连 |

---

## REST API

> 所有响应统一格式：`{"success": true, "data": {...}, "error": null}`
> 失败时：`{"success": false, "data": null, "error": {"code": "XXX", "message": "..."}}`

### Skill 管理

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/claude/skills/upload` | 上传 `.skill` 文件（multipart，字段名 `file`） |
| GET | `/claude/skills` | 列出所有已注册 skill |
| GET | `/claude/skills/{name}` | 查看 skill 详情 |
| DELETE | `/claude/skills/{name}` | 删除 skill |
| POST | `/claude/skills/{name}/reload` | 从磁盘热重载 |

### 会话管理

| 方法 | 路径 | 请求体 / 参数 | 说明 |
|------|------|------|------|
| POST | `/claude/sessions` | `{"skillName": "..."}` | 创建会话，`skillName` 可选 |
| GET | `/claude/sessions` | — | 列出所有会话 ID |
| GET | `/claude/sessions/{id}` | — | 会话详情 |
| DELETE | `/claude/sessions/{id}` | — | 删除会话 |
| POST | `/claude/sessions/{id}/clear` | — | 清空会话历史（保留会话） |

### 对话（纯文本）

| 方法 | 路径 | 请求体 | 说明 |
|------|------|------|------|
| POST | `/claude/chat` | `{"content": "..."}` | 自动建会话 + 普通对话 |
| POST | `/claude/chat-with-skill` | `{"skillName","content"}` | 自动建会话 + skill 对话 |
| POST | `/claude/sessions/{id}/chat` | `{"content": "..."}` | 指定会话的普通对话 |
| POST | `/claude/sessions/{id}/chat-with-skill` | `{"skillName?","content"}` | 指定会话的 skill 对话（skillName 可覆盖会话绑定值） |
| POST | `/claude/execute` | `{"skillName","text"}` | 无会话单次 skill 执行 |

### 对话（含文件）

| 方法 | 路径 | 请求类型 | 说明 |
|------|------|------|------|
| POST | `/claude/files` | multipart，字段 `file` | 仅上传，返回 `fileId` |
| POST | `/claude/chat-with-file` | multipart | 自动建会话 + 可选文件 + 可选 skill |
| POST | `/claude/sessions/{id}/chat-with-file` | multipart | 指定会话 + 可选文件（多轮追问同一文件） |

**`chat-with-file` 支持的参数**（multipart/form-data 字段）：

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `content` | string | ✓ | 用户文本 |
| `skillName` | string | | 绑定 skill |
| `file` | binary | | 本次要上传的文件 |
| `fileId` | string | | 已上传文件的 ID（与 `file` 二选一） |
| `mimeType` | string | | 使用已有 `fileId` 时必填 |

**支持的文件类型：**

| 类型 | MIME | Content Block |
|------|------|------|
| PDF | `application/pdf` | `document` |
| 图片 | `image/jpeg` `image/png` `image/gif` `image/webp` | `image` |

> 其他类型（txt / md / csv / docx / xlsx）Anthropic 不支持通过 `file_id` 引用，建议先转成 PDF 或直接把文本内联到 `content`。

---

## 调用示例

### 会话与 Skill

```bash
# 创建绑定 skill 的会话
curl -X POST http://localhost:8080/claude/sessions \
  -H "Content-Type: application/json" \
  -d '{"skillName":"ai-rewrite"}'
# → {"data":{"sessionId":"xxxx-xxxx",...}}

# 多轮对话
curl -X POST http://localhost:8080/claude/sessions/xxxx-xxxx/chat-with-skill \
  -H "Content-Type: application/json" \
  -d '{"content":"请帮我降低这段文字的AI检测率：..."}'
```

### 文件对话（推荐：先上传再复用）

```bash
# ① 一次性上传
curl -X POST http://localhost:8080/claude/files -F "file=@thesis.pdf"
# → {"data":{"fileId":"file_01abc...","mimeType":"application/pdf",...}}

# ② 多轮追问，只传 fileId（省带宽）
curl -X POST http://localhost:8080/claude/chat-with-file \
  -F "content=这份论文的核心论点是什么？" \
  -F "fileId=file_01abc..." \
  -F "mimeType=application/pdf"
# → {"data":{"sessionId":"yyyy","reply":"...","fileId":"file_01abc..."}}

# ③ 对同一份文件持续追问（使用 sessionId）
curl -X POST http://localhost:8080/claude/sessions/yyyy/chat-with-file \
  -F "content=第三章的实验数据是如何采集的？" \
  -F "fileId=file_01abc..." \
  -F "mimeType=application/pdf"
```

### 文件对话（简化：一次完成上传+对话）

```bash
curl -X POST http://localhost:8080/claude/chat-with-file \
  -F "content=总结这份文档" \
  -F "skillName=ai-rewrite" \
  -F "file=@thesis.pdf"
```

### 自动建会话

```bash
# 普通对话
curl -X POST http://localhost:8080/claude/chat \
  -H "Content-Type: application/json" \
  -d '{"content":"你好"}'

# Skill 对话
curl -X POST http://localhost:8080/claude/chat-with-skill \
  -H "Content-Type: application/json" \
  -d '{"skillName":"ai-rewrite","content":"请润色这段文字"}'
```

### 无会话单次执行

```bash
curl -X POST http://localhost:8080/claude/execute \
  -H "Content-Type: application/json" \
  -d '{"skillName":"ai-rewrite","text":"需要改写的文本..."}'
```

---

## 扩展指南

### 自定义 SessionStore（Redis / 数据库）

```java
@Bean
public SessionStore redisSessionStore(RedisTemplate<String, Object> redis) {
    return new RedisSessionStore(redis);  // 实现 SessionStore 接口
}
```

Starter 检测到你的 Bean 后自动跳过默认内存实现。

### 自定义 Tool 执行器

```java
@Service
public class MyClaudeService extends ClaudeSkillService {

    public MyClaudeService(ClaudeClient client, SkillRegistry registry,
                           SessionStore store, ClaudeProperties props) {
        super(client, registry, store, props);
    }

    @Override
    protected String executeTool(String toolName, Map<String, Object> input) {
        return switch (toolName) {
            case "query_order" -> orderService.query((String) input.get("id"));
            default -> super.executeTool(toolName, input);
        };
    }
}
```

### 关闭内置 REST 端点

```yaml
claude:
  web:
    enabled: false
```

然后在自己的 Controller 注入 `ClaudeSkillService` 直接调用。

### 修改 API 路径前缀

```yaml
claude:
  web:
    base-path: /api/v1/ai
```

---

## Skill 文件格式

`.skill` 实际是一个 ZIP 包：

```
my-skill.skill
├── SKILL.md              ← 必须，含 YAML frontmatter
└── references/           ← 可选，自动合并到 system prompt
    ├── ref1.md
    └── ref2.md
```

**SKILL.md 示例：**

```markdown
---
name: my-skill
description: >
  一行或多行描述，告诉 Claude 什么时候使用此 skill
---

# 指令正文
Claude 的详细执行指令...
```

---

## FAQ

**Q: 上传 `.docx` 文件时报错 "Unsupported file type"**
A: Anthropic Files API 仅支持 PDF 和图片通过 `file_id` 引用。建议：
- 用 Word 或在线工具转成 PDF 后再上传
- 或者把 docx 文本读出后直接作为 `content` 传入

**Q: 如何在多次调用间复用文件？**
A: 先调 `POST /claude/files` 拿 `fileId`，后续 chat-with-file 只传 `fileId` + `mimeType`，不要重复上传——每次重传都会产生新的 fileId。

**Q: 代理配置正确但仍无法访问 API**
A: 按优先级排查：
1. `claude.proxy.mode` 当前是什么？`NONE` 会强制忽略系统代理
2. `CUSTOM` 模式下 `proxy.host` 和 `proxy.port` 是否填了
3. 启动日志会打印 `[Claude] Proxy: ...`，确认是否符合预期
4. 若走 SOCKS5 代理，`proxy.type` 必须显式写 `SOCKS5`

**Q: 会话会自动清理吗？**
A: 会。按 `session.max-size` LRU 淘汰，按 `session.ttl-minutes` 过期（0 关闭）。每个会话消息数受 `max-turns-per-session` 限制，超过时自动丢弃最早消息。

**Q: 文件上传支持多大？**
A: Anthropic 官方限制单文件 500MB、账号存储总量 500GB。Spring Boot 默认 multipart 限制更小（1MB），若上传大文件需在主项目中调整：

```yaml
spring:
  servlet:
    multipart:
      max-file-size: 100MB
      max-request-size: 100MB
```
