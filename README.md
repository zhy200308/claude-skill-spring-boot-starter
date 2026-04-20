# claude-skill-spring-boot-starter
Claude 携带skill对话
=======
# Claude Skill Spring Boot Starter

一个可直接以 JAR 形式集成到任意 Spring Boot 项目的 Claude API 封装库。
提供 **Skill 管理**、**多轮会话存储**、**代理配置**、**REST 端点** 开箱即用。

---

## 快速集成

### 1. 安装到本地 Maven 仓库
```bash
cd claude-skill-spring-boot-starter
mvn clean install -DskipTests
```

### 2. 在你的项目中引入依赖
```xml
<dependency>
    <groupId>com.claude.skill</groupId>
    <artifactId>claude-skill-spring-boot-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

### 3. 最少配置（application.yml）
```yaml
claude:
  api-key: sk-ant-xxxxxxxxxxxxxxxx
```

完成！在 Web 应用且 `claude.web.enabled=true` 时自动注册接口。

---

## 配置说明

```yaml
claude:
  api-key: ${CLAUDE_API_KEY}         # 必填
  model: claude-sonnet-4-20250514    # 默认模型
  max-tokens: 4096
  timeout-seconds: 120

  # 代理（三种模式）
  proxy:
    mode: SYSTEM     # SYSTEM(默认) | CUSTOM | NONE

    # CUSTOM 模式才需填写：
    # host: 127.0.0.1
    # port: 7890
    # type: HTTP     # HTTP | SOCKS5
    # username:
    # password:

  # Skill 文件目录
  skill:
    dir: skills
    auto-load: true

  # 会话存储
  session:
    max-size: 1000
    ttl-minutes: 60
    max-turns-per-session: 50

  # REST 端点
  web:
    enabled: true
    base-path: /claude
    springdoc:
      enabled: true
      group: claude-skill
      package-to-scan: com.claude.skill.web
```

### SpringDoc / Knife4j 集成行为

- 当主项目已集成 SpringDoc 且 `springdoc.api-docs.enabled=true` 时，Starter 自动注册 `claude-skill` 分组。
- 分组默认匹配 `claude.web.base-path` 下全部接口，默认是 `/claude/**`。
- 当主项目关闭 `springdoc.api-docs.enabled=false` 时，Starter 不注册文档分组。
- 当主项目集成 Knife4j 时，该分组会自动出现在 Knife4j 面板中，无需额外挂载配置。

### Skill 上传与存储路径

- 上传 `.skill` 文件后会先持久化到本地目录，再注册到内存。
- `claude.skill.dir` 未配置或为相对路径时，基于主项目 `src/main/resources` 解析。
- 默认目录是 `src/main/resources/skills`，目录不存在会自动创建。
- 当 `claude.skill.dir` 配置为绝对路径时，直接使用该路径。

---

## 系统代理配置方式（SYSTEM 模式）

Starter 默认使用系统代理，按以下优先级自动检测：

| 优先级 | 方式 | 示例 |
|--------|------|------|
| 1 | JVM 启动参数 | `-Dhttps.proxyHost=127.0.0.1 -Dhttps.proxyPort=7890` |
| 2 | 环境变量 | `HTTPS_PROXY=http://127.0.0.1:7890` |
| 3 | 无代理 | 直连 |

强制直连：`claude.proxy.mode: NONE`

---

## REST API 文档

### Skill 管理

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/claude/skills/upload` | 上传 .skill 文件（multipart/form-data，字段名 file） |
| GET | `/claude/skills` | 列出所有已注册 skill |
| GET | `/claude/skills/{name}` | 查看 skill 详情 |
| DELETE | `/claude/skills/{name}` | 删除 skill |
| POST | `/claude/skills/{name}/reload` | 从磁盘热重载 |

### 会话管理

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/claude/sessions` | 创建会话（body: `{"skillName": "ai-rewrite"}`，skillName 可选） |
| GET | `/claude/sessions` | 列出所有会话 ID |
| GET | `/claude/sessions/{id}` | 获取会话信息 |
| DELETE | `/claude/sessions/{id}` | 删除会话 |
| POST | `/claude/sessions/{id}/clear` | 清空会话历史 |

### 对话

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/claude/sessions/{id}/chat` | 普通多轮对话 |
| POST | `/claude/chat` | 自动创建会话并对话（只需传 `content`） |
| POST | `/claude/chat-with-skill` | 自动创建绑定 skill 的会话并对话（传 `skillName + content`） |
| POST | `/claude/sessions/{id}/chat-with-skill` | 携带 skill system prompt 的对话 |
| POST | `/claude/execute` | 无会话单次执行 skill |

---

## 调用示例

### 上传 Skill
```bash
curl -X POST http://localhost:8080/claude/skills/upload \
  -F "file=@ai-rewrite.skill"
```

### 创建绑定 Skill 的会话
```bash
curl -X POST http://localhost:8080/claude/sessions \
  -H "Content-Type: application/json" \
  -d '{"skillName": "ai-rewrite"}'
# 返回: {"data": {"sessionId": "xxxx-xxxx", ...}}
```

### 多轮对话
```bash
curl -X POST http://localhost:8080/claude/sessions/{sessionId}/chat-with-skill \
  -H "Content-Type: application/json" \
  -d '{"content": "请帮我降低这段文字的AI检测率：xxx"}'
```

### 自动创建会话并对话
```bash
curl -X POST http://localhost:8080/claude/chat \
  -H "Content-Type: application/json" \
  -d '{"content": "你好，请帮我总结这段文本"}'
```

### 自动创建会话并用 skill 对话
```bash
curl -X POST http://localhost:8080/claude/chat-with-skill \
  -H "Content-Type: application/json" \
  -d '{"skillName": "ai-rewrite", "content": "请帮我润色这段文字"}'
```

### 无会话单次执行
```bash
curl -X POST http://localhost:8080/claude/execute \
  -H "Content-Type: application/json" \
  -d '{"skillName": "ai-rewrite", "text": "需要改写的文本..."}'
```

### 请求体参数说明（JSON）

```jsonc
// POST /claude/sessions
{
  "skillName": "ai-rewrite" // 可选，创建会话时绑定 skill
}
```

```jsonc
// POST /claude/chat
{
  "content": "你好，请帮我总结这段文本" // 必填，聊天内容
}
```

```jsonc
// POST /claude/chat-with-skill
{
  "skillName": "ai-rewrite", // 必填，指定 skill
  "content": "请帮我润色这段文字" // 必填，聊天内容
}
```

```jsonc
// POST /claude/sessions/{id}/chat
{
  "content": "继续上一次会话" // 必填，聊天内容
}
```

```jsonc
// POST /claude/sessions/{id}/chat-with-skill
{
  "skillName": "ai-rewrite", // 可选，临时覆盖会话绑定 skill
  "content": "按会话绑定或指定 skill 处理" // 必填，聊天内容
}
```

```jsonc
// POST /claude/execute
{
  "skillName": "ai-rewrite", // 必填，指定 skill
  "text": "需要改写的文本..." // 必填，待处理文本
}
```

---

## 扩展指南

### 1. 自定义 SessionStore（接入 Redis / 数据库）

```java
@Bean
public SessionStore redisSessionStore(RedisTemplate<String, Object> redis) {
    return new RedisSessionStore(redis);  // 实现 SessionStore 接口
}
```
Starter 检测到你的 Bean 后自动跳过默认内存实现。

### 2. 自定义 Tool 执行器

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

### 3. 关闭内置 REST 端点
```yaml
claude:
  web:
    enabled: false
```
然后直接注入 `ClaudeSkillService` 在自己的 Controller 中使用。

### 4. 修改 API 路径前缀
```yaml
claude:
  web:
    base-path: /api/v1/ai
```

---

## .skill 文件格式

```
my-skill.skill (ZIP)
├── SKILL.md              ← 必须，含 YAML frontmatter
└── references/           ← 可选，自动合并到 system prompt
    ├── ref1.md
    └── ref2.md
```

SKILL.md 格式：
```markdown
---
name: my-skill
description: >
  一行或多行描述，告诉 Claude 什么时候使用此 skill
---

# 指令正文
Claude 的详细执行指令...
```
>>>>>>> 8f720da (完善携带skill进行对话)
