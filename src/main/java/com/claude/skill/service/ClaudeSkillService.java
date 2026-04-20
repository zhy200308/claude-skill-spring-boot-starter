package com.claude.skill.service;

import com.claude.skill.config.ClaudeProperties;
import com.claude.skill.core.client.ClaudeClient;
import com.claude.skill.core.session.SessionStore;
import com.claude.skill.core.skill.SkillRegistry;
import com.claude.skill.model.*;

import java.io.IOException;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Claude Skill 核心服务
 *
 * 提供三种调用模式：
 *   1. executeSkill()     — 无会话，单次调用指定 skill
 *   2. chat()             — 多轮会话，不绑定 skill
 *   3. chatWithSkill()    — 多轮会话 + 绑定 skill 的 system prompt
 */
public class ClaudeSkillService {

    private static final Logger log = Logger.getLogger(ClaudeSkillService.class.getName());

    private final ClaudeClient     client;
    private final SkillRegistry    skillRegistry;
    private final SessionStore     sessionStore;
    private final ClaudeProperties props;

    public ClaudeSkillService(ClaudeClient client,
                               SkillRegistry skillRegistry,
                               SessionStore sessionStore,
                               ClaudeProperties props) {
        this.client        = client;
        this.skillRegistry = skillRegistry;
        this.sessionStore  = sessionStore;
        this.props         = props;
    }

    // ─── 模式1：无会话单次执行 Skill ─────────────────────────────────────

    /**
     * 用指定 skill 处理一段文本（无会话状态）
     * skill 的 SKILL.md 内容作为 system prompt 注入
     */
    public String executeSkill(String skillName, String userText) throws IOException, InterruptedException {
        SkillPackage skill = skillRegistry.get(skillName)
            .orElseThrow(() -> new NoSuchElementException("Skill not found: " + skillName));

        Map<String, Object> requestBody = buildRequest(
            skill.getSystemPrompt(),
            List.of(new ChatMessage("user", userText)),
            null,  // 无 tools
            null
        );

        ClaudeResponse response = client.send(requestBody);
        return response.extractText();
    }

    // ─── 模式2：多轮会话（不绑定 skill）────────────────────────────────────

    /**
     * 创建新会话
     * @param skillName 绑定的 skill 名（可为 null）
     */
    public ConversationSession createSession(String skillName) {
        return sessionStore.create(skillName);
    }

    /**
     * 在已有会话中发送消息
     */
    public String chat(String sessionId, String userMessage) throws IOException, InterruptedException {
        ConversationSession session = sessionStore.get(sessionId)
            .orElseThrow(() -> new NoSuchElementException("Session not found: " + sessionId));

        return chatInternal(session, userMessage, false, null);
    }

    // ─── 模式3：多轮会话 + Skill system prompt ────────────────────────────

    /**
     * 在已有会话中发送消息，并注入绑定 skill 的 system prompt
     * 若会话无绑定 skill，则退化为普通 chat
     */
    public String chatWithSkill(String sessionId, String userMessage) throws IOException, InterruptedException {
        return chatWithSkill(sessionId, userMessage, null);
    }

    /**
     * 在已有会话中发送消息，并显式指定 skill（优先于会话绑定）
     */
    public String chatWithSkill(String sessionId, String userMessage, String skillName)
            throws IOException, InterruptedException {
        ConversationSession session = sessionStore.get(sessionId)
            .orElseThrow(() -> new NoSuchElementException("Session not found: " + sessionId));

        return chatInternal(session, userMessage, true, skillName);
    }

    // ─── 内部：多轮对话核心（支持 tool_use 循环）─────────────────────────

    private String chatInternal(ConversationSession session,
                                 String userMessage,
                                 boolean useSkillSystem,
                                 String explicitSkillName)
            throws IOException, InterruptedException {

        // 加入用户消息
        session.addMessage(new ChatMessage("user", userMessage));

        // 确定 system prompt
        String systemPrompt = null;
        if (useSkillSystem) {
            String skillName = (explicitSkillName != null && !explicitSkillName.isBlank())
                ? explicitSkillName
                : session.getSkillName();
            if (skillName == null || skillName.isBlank()) {
                throw new IllegalArgumentException("No skill specified. Bind skill when creating session or pass skillName.");
            }
            systemPrompt = skillRegistry.getSystemPrompt(skillName)
                .orElseThrow(() -> new NoSuchElementException("Skill not found: " + skillName));
        }

        String finalText = "";

        // tool_use 多轮循环（最多5轮防死循环）
        for (int round = 0; round < 5; round++) {
            Map<String, Object> requestBody = buildRequest(
                systemPrompt,
                session.getMessagesSnapshot(),
                null,  // 当前版本不做 tool_use 路由，skill 直接做 system
                null
            );

            ClaudeResponse response = client.send(requestBody);

            if (response.isFinished()) {
                finalText = response.extractText();
                session.addMessage(new ChatMessage("assistant", finalText));
                break;
            }

            if (response.needsToolCall()) {
                // 将 assistant 的 tool_use 消息加入历史
                session.addMessage(new ChatMessage("assistant", response.getContent()));

                // 执行 tool，构建 tool_result
                List<Map<String, Object>> toolResults = processToolCalls(response);
                session.addMessage(new ChatMessage("user", toolResults));
                // 继续循环
            } else {
                // 其他 stop_reason（max_tokens 等），直接取文本
                finalText = response.extractText();
                session.addMessage(new ChatMessage("assistant", finalText));
                break;
            }
        }

        sessionStore.save(session);
        return finalText;
    }

    // ─── Tool 调用处理（框架预留，集成方可扩展）────────────────────────────

    /**
     * 处理 Claude 返回的 tool_use block
     * 集成方可继承 ClaudeSkillService 并重写此方法，接入自己的业务逻辑
     */
    protected List<Map<String, Object>> processToolCalls(ClaudeResponse response) {
        List<Map<String, Object>> results = new ArrayList<>();
        for (ClaudeResponse.ContentBlock block : response.getContent()) {
            if (block.isToolUse()) {
                String result = executeTool(block.getName(), block.getInput());
                results.add(Map.of(
                    "type",        "tool_result",
                    "tool_use_id", block.getId(),
                    "content",     result
                ));
            }
        }
        return results;
    }

    /**
     * 执行单个 tool（默认返回未实现提示）
     * 集成方重写此方法即可注入自己的业务 Skill 执行器
     */
    protected String executeTool(String toolName, Map<String, Object> input) {
        log.warning("No tool executor registered for: " + toolName);
        return "{\"error\": \"Tool not implemented: " + toolName + "\"}";
    }

    // ─── 会话管理 ─────────────────────────────────────────────────────────

    public Optional<ConversationSession> getSession(String sessionId) {
        return sessionStore.get(sessionId);
    }

    public void deleteSession(String sessionId) {
        sessionStore.delete(sessionId);
    }

    public void clearSession(String sessionId) {
        sessionStore.get(sessionId).ifPresent(s -> {
            s.clear();
            sessionStore.save(s);
        });
    }

    public List<String> listSessionIds() {
        return sessionStore.listSessionIds();
    }

    // ─── 请求体构建 ───────────────────────────────────────────────────────

    private Map<String, Object> buildRequest(String systemPrompt,
                                              List<ChatMessage> messages,
                                              List<Map<String, Object>> tools,
                                              String toolChoice) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model",      props.getModel());
        body.put("max_tokens", props.getMaxTokens());

        if (systemPrompt != null && !systemPrompt.isBlank()) {
            body.put("system", systemPrompt);
        }
        body.put("messages", messages);

        if (tools != null && !tools.isEmpty()) {
            body.put("tools", tools);
            if (toolChoice != null) {
                body.put("tool_choice", Map.of("type", toolChoice));
            }
        }
        return body;
    }
}
