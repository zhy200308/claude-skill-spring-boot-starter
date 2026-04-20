package com.claude.skill.web;

import com.claude.skill.model.ApiResult;
import com.claude.skill.model.ConversationSession;
import com.claude.skill.service.ClaudeSkillService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 对话管理接口
 *
 * POST /claude/sessions                      — 创建会话
 * POST /claude/sessions/{id}/chat            — 多轮对话
 * POST /claude/sessions/{id}/chat-with-skill — 携带 skill 的多轮对话
 * POST /claude/execute                       — 无会话单次执行 skill
 * GET  /claude/sessions                      — 列出所有会话ID
 * GET  /claude/sessions/{id}                 — 获取会话信息
 * DELETE /claude/sessions/{id}              — 删除会话
 * POST /claude/sessions/{id}/clear          — 清空会话历史
 */
@RestController
public class ChatController {

    private final ClaudeSkillService claudeSkillService;

    public ChatController(ClaudeSkillService claudeSkillService) {
        this.claudeSkillService = claudeSkillService;
    }

    public static class CreateSessionRequest {
        public String skillName; // 可选，绑定的 skill 名称
    }

    public static class ChatRequest {
        public String content; // 必填，用户输入内容
    }

    public static class ChatWithSkillRequest {
        public String skillName; // 必填，skill 名称
        public String content;   // 必填，用户输入内容
    }

    public static class SessionChatWithSkillRequest {
        public String skillName; // 可选，显式指定 skill；不传则使用会话绑定 skill
        public String content;   // 必填，用户输入内容
    }

    public static class ExecuteRequest {
        public String skillName; // 必填，skill 名称
        public String text;      // 必填，待处理文本
    }

    // ─── 会话管理 ─────────────────────────────────────────────────────────

    /** 创建新会话（可选绑定 skillName） */
    @PostMapping(value = "${claude.web.base-path:/claude}/sessions", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResult<Map<String, String>> createSession(
            @RequestBody(required = false) CreateSessionRequest body) {
        String skillName = body != null ? body.skillName : null;
        ConversationSession session = claudeSkillService.createSession(skillName);
        return ApiResult.ok(Map.of(
            "sessionId",  session.getSessionId(),
            "skillName",  skillName != null ? skillName : "",
            "createdAt",  session.getCreatedAt().toString()
        ));
    }

    /** 列出所有会话 ID */
    @GetMapping("${claude.web.base-path:/claude}/sessions")
    public ApiResult<List<String>> listSessions() {
        return ApiResult.ok(claudeSkillService.listSessionIds());
    }

    /** 获取会话信息 */
    @GetMapping("${claude.web.base-path:/claude}/sessions/{sessionId}")
    public ApiResult<Map<String, Object>> getSession(@PathVariable String sessionId) {
        return claudeSkillService.getSession(sessionId)
            .map(s -> ApiResult.ok(Map.<String, Object>of(
                "sessionId",    s.getSessionId(),
                "skillName",    s.getSkillName() != null ? s.getSkillName() : "",
                "messageCount", s.getMessageCount(),
                "createdAt",    s.getCreatedAt().toString(),
                "lastActiveAt", s.getLastActiveAt().toString()
            )))
            .orElse(ApiResult.fail("NOT_FOUND", "Session not found: " + sessionId));
    }

    /** 删除会话 */
    @DeleteMapping("${claude.web.base-path:/claude}/sessions/{sessionId}")
    public ApiResult<Void> deleteSession(@PathVariable String sessionId) {
        claudeSkillService.deleteSession(sessionId);
        return ApiResult.ok(null);
    }

    /** 清空会话历史（保留会话） */
    @PostMapping("${claude.web.base-path:/claude}/sessions/{sessionId}/clear")
    public ApiResult<Void> clearSession(@PathVariable String sessionId) {
        claudeSkillService.clearSession(sessionId);
        return ApiResult.ok("Session cleared", null);
    }

    // ─── 对话 ─────────────────────────────────────────────────────────────

    /** 普通多轮对话 */
    @PostMapping(value = "${claude.web.base-path:/claude}/sessions/{sessionId}/chat", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResult<Map<String, String>> chat(
            @PathVariable String sessionId,
            @RequestBody ChatRequest body) {
        String message = body != null ? body.content : null;
        if (message == null || message.isBlank()) {
            return ApiResult.fail("INVALID_INPUT", "content is required");
        }
        try {
            String reply = claudeSkillService.chat(sessionId, message);
            return ApiResult.ok(Map.of("reply", reply, "sessionId", sessionId));
        } catch (Exception e) {
            return ApiResult.fail("CHAT_ERROR", e.getMessage());
        }
    }

    @PostMapping(value = "${claude.web.base-path:/claude}/chat", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResult<Map<String, String>> autoSessionChat(@RequestBody ChatRequest body) {
        String message = body != null ? body.content : null;
        if (message == null || message.isBlank()) {
            return ApiResult.fail("INVALID_INPUT", "content is required");
        }
        try {
            ConversationSession session = claudeSkillService.createSession(null);
            String sessionId = session.getSessionId();
            String reply = claudeSkillService.chat(sessionId, message);
            return ApiResult.ok(Map.of("sessionId", sessionId, "reply", reply));
        } catch (Exception e) {
            return ApiResult.fail("CHAT_ERROR", e.getMessage());
        }
    }

    @PostMapping(value = "${claude.web.base-path:/claude}/chat-with-skill", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResult<Map<String, String>> autoSessionChatWithSkill(@RequestBody ChatWithSkillRequest body) {
        String skillName = body != null ? body.skillName : null;
        String message = body != null ? body.content : null;
        if (message == null || message.isBlank()) {
            return ApiResult.fail("INVALID_INPUT", "content is required");
        }
        if (skillName == null || skillName.isBlank()) {
            return ApiResult.fail("INVALID_INPUT", "skillName is required");
        }
        try {
            ConversationSession session = claudeSkillService.createSession(skillName);
            String sessionId = session.getSessionId();
            String reply = claudeSkillService.chatWithSkill(sessionId, message);
            return ApiResult.ok(Map.of("sessionId", sessionId, "skillName", skillName, "reply", reply));
        } catch (Exception e) {
            return ApiResult.fail("CHAT_ERROR", e.getMessage());
        }
    }

    /** 携带 skill system prompt 的多轮对话 */
    @PostMapping(value = "${claude.web.base-path:/claude}/sessions/{sessionId}/chat-with-skill", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResult<Map<String, String>> chatWithSkill(
            @PathVariable String sessionId,
            @RequestBody SessionChatWithSkillRequest body) {
        String message = body != null ? body.content : null;
        String skillName = body != null ? body.skillName : null;
        if (message == null || message.isBlank()) {
            return ApiResult.fail("INVALID_INPUT", "content is required");
        }
        try {
            String reply = claudeSkillService.chatWithSkill(sessionId, message, skillName);
            return ApiResult.ok(Map.of("reply", reply, "sessionId", sessionId));
        } catch (Exception e) {
            return ApiResult.fail("CHAT_ERROR", e.getMessage());
        }
    }

    /** 无会话单次 skill 执行 */
    @PostMapping(value = "${claude.web.base-path:/claude}/execute", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResult<Map<String, String>> execute(@RequestBody ExecuteRequest body) {
        String skillName = body != null ? body.skillName : null;
        String text = body != null ? body.text : null;
        if (skillName == null || text == null) {
            return ApiResult.fail("INVALID_INPUT", "skillName and text are required");
        }
        try {
            String result = claudeSkillService.executeSkill(skillName, text);
            return ApiResult.ok(Map.of("result", result, "skillName", skillName));
        } catch (Exception e) {
            return ApiResult.fail("EXECUTE_ERROR", e.getMessage());
        }
    }
}
