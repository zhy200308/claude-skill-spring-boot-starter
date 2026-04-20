package com.claude.skill.web;

import com.claude.skill.core.client.ClaudeClient;
import com.claude.skill.model.ApiResult;
import com.claude.skill.model.ConversationSession;
import com.claude.skill.service.ClaudeSkillService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * 对话管理接口
 *
 * 会话管理：
 *   POST   /claude/sessions                         — 创建会话
 *   GET    /claude/sessions                         — 列出会话 ID
 *   GET    /claude/sessions/{id}                    — 会话详情
 *   DELETE /claude/sessions/{id}                    — 删除会话
 *   POST   /claude/sessions/{id}/clear              — 清空会话历史
 *
 * 对话：
 *   POST   /claude/chat                             — 自动建会话的普通对话
 *   POST   /claude/chat-with-skill                  — 自动建会话的 skill 对话
 *   POST   /claude/sessions/{id}/chat               — 指定会话的普通对话
 *   POST   /claude/sessions/{id}/chat-with-skill    — 指定会话的 skill 对话
 *   POST   /claude/execute                          — 无会话单次 skill 执行
 *
 * 文件：
 *   POST   /claude/files                            — 上传文件，返回 file_id
 *   POST   /claude/chat-with-file                   — 自动建会话 + 可选文件 + 可选 skill
 *   POST   /claude/sessions/{id}/chat-with-file     — 指定会话 + 可选文件 + 可选 skill
 */
@RestController
public class ChatController {

    private final ClaudeSkillService claudeSkillService;

    public ChatController(ClaudeSkillService claudeSkillService) {
        this.claudeSkillService = claudeSkillService;
    }

    // ─── DTO ─────────────────────────────────────────────────────────────

    public static class CreateSessionRequest {
        public String skillName;
    }

    public static class ChatRequest {
        public String content;
    }

    public static class ChatWithSkillRequest {
        public String skillName;
        public String content;
    }

    public static class SessionChatWithSkillRequest {
        public String skillName;
        public String content;
    }

    public static class ExecuteRequest {
        public String skillName;
        public String text;
    }

    // ─── 会话管理 ─────────────────────────────────────────────────────────

    @PostMapping(value = "${claude.web.base-path:/claude}/sessions", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResult<Map<String, String>> createSession(
            @RequestBody(required = false) CreateSessionRequest body) {
        String skillName = body != null ? body.skillName : null;
        ConversationSession session = claudeSkillService.createSession(skillName);
        return ApiResult.ok(Map.of(
                "sessionId", session.getSessionId(),
                "skillName", skillName != null ? skillName : "",
                "createdAt", session.getCreatedAt().toString()
        ));
    }

    @GetMapping("${claude.web.base-path:/claude}/sessions")
    public ApiResult<List<String>> listSessions() {
        return ApiResult.ok(claudeSkillService.listSessionIds());
    }

    @GetMapping("${claude.web.base-path:/claude}/sessions/{sessionId}")
    public ApiResult<Map<String, Object>> getSession(@PathVariable String sessionId) {
        return claudeSkillService.getSession(sessionId)
                .map(s -> ApiResult.ok(Map.<String, Object>of(
                        "sessionId", s.getSessionId(),
                        "skillName", s.getSkillName() != null ? s.getSkillName() : "",
                        "messageCount", s.getMessageCount(),
                        "createdAt", s.getCreatedAt().toString(),
                        "lastActiveAt", s.getLastActiveAt().toString()
                )))
                .orElse(ApiResult.fail("NOT_FOUND", "Session not found: " + sessionId));
    }

    @DeleteMapping("${claude.web.base-path:/claude}/sessions/{sessionId}")
    public ApiResult<Void> deleteSession(@PathVariable String sessionId) {
        claudeSkillService.deleteSession(sessionId);
        return ApiResult.ok(null);
    }

    @PostMapping("${claude.web.base-path:/claude}/sessions/{sessionId}/clear")
    public ApiResult<Void> clearSession(@PathVariable String sessionId) {
        claudeSkillService.clearSession(sessionId);
        return ApiResult.ok("Session cleared", null);
    }

    // ─── 对话（JSON） ─────────────────────────────────────────────────────

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

    // ─── 文件上传 ─────────────────────────────────────────────────────────

    /**
     * 仅上传文件，返回 file_id 及元信息
     * 前端可先上传拿到 file_id，再在多次对话中复用
     */
    @PostMapping(value = "${claude.web.base-path:/claude}/files",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResult<Map<String, Object>> uploadFile(@RequestPart("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return ApiResult.fail("INVALID_INPUT", "file is required");
        }
        try {
            ClaudeClient.FileUploadResult r = claudeSkillService.uploadFileWithMeta(file);
            return ApiResult.ok(Map.of(
                    "fileId",    r.getFileId(),
                    "filename",  r.getFilename() != null ? r.getFilename() : "",
                    "mimeType",  r.getMimeType() != null ? r.getMimeType() : "",
                    "sizeBytes", r.getSizeBytes()
            ));
        } catch (Exception e) {
            return ApiResult.fail("UPLOAD_ERROR", e.getMessage());
        }
    }

    // ─── 携带文件的对话 ───────────────────────────────────────────────────

    /**
     * 自动建会话 + 可选文件 + 可选 skill
     *
     * 参数（multipart/form-data）：
     *   content   : 必填，用户文本
     *   skillName : 可选，绑定 skill
     *   file      : 可选，要随本次对话上传的文件
     *   fileId    : 可选，已上传文件的 file_id（与 file 二选一）
     *   mimeType  : 可选，使用已上传 file_id 时必填（例如 application/pdf）
     *
     * 4 种组合自动路由：
     *   无 skill + 无 file  → 普通对话
     *   有 skill + 无 file  → skill 对话
     *   无 skill + 有 file  → 文件对话
     *   有 skill + 有 file  → skill + 文件对话
     */
    @PostMapping(value = "${claude.web.base-path:/claude}/chat-with-file",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResult<Map<String, Object>> autoSessionChatWithFile(
            @RequestParam("content") String content,
            @RequestParam(value = "skillName", required = false) String skillName,
            @RequestParam(value = "fileId",    required = false) String fileId,
            @RequestParam(value = "mimeType",  required = false) String mimeType,
            @RequestPart(value = "file",       required = false) MultipartFile file) {

        if (content == null || content.isBlank()) {
            return ApiResult.fail("INVALID_INPUT", "content is required");
        }

        try {
            // 若提供了文件 → 先上传拿到 fileId 与 mimeType
            if (file != null && !file.isEmpty()) {
                ClaudeClient.FileUploadResult r = claudeSkillService.uploadFileWithMeta(file);
                fileId   = r.getFileId();
                mimeType = r.getMimeType();
            }

            // 建会话
            ConversationSession session = claudeSkillService.createSession(skillName);
            String sessionId = session.getSessionId();

            // 统一走 chatWithFile（fileId 为 null 时自动退化为普通对话）
            String reply = claudeSkillService.chatWithFile(
                    sessionId, content, fileId, mimeType, skillName
            );

            Map<String, Object> result = new java.util.LinkedHashMap<>();
            result.put("sessionId", sessionId);
            result.put("reply", reply);
            if (skillName != null && !skillName.isBlank()) result.put("skillName", skillName);
            if (fileId != null && !fileId.isBlank())       result.put("fileId", fileId);
            return ApiResult.ok(result);

        } catch (IllegalArgumentException e) {
            return ApiResult.fail("INVALID_INPUT", e.getMessage());
        } catch (Exception e) {
            return ApiResult.fail("CHAT_ERROR", e.getMessage());
        }
    }

    /**
     * 指定会话 + 可选文件 + 可选 skill
     * 适用于连续对一份文件做多轮追问的场景
     */
    @PostMapping(value = "${claude.web.base-path:/claude}/sessions/{sessionId}/chat-with-file",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResult<Map<String, Object>> sessionChatWithFile(
            @PathVariable String sessionId,
            @RequestParam("content") String content,
            @RequestParam(value = "skillName", required = false) String skillName,
            @RequestParam(value = "fileId",    required = false) String fileId,
            @RequestParam(value = "mimeType",  required = false) String mimeType,
            @RequestPart(value = "file",       required = false) MultipartFile file) {

        if (content == null || content.isBlank()) {
            return ApiResult.fail("INVALID_INPUT", "content is required");
        }

        try {
            if (file != null && !file.isEmpty()) {
                ClaudeClient.FileUploadResult r = claudeSkillService.uploadFileWithMeta(file);
                fileId   = r.getFileId();
                mimeType = r.getMimeType();
            }

            String reply = claudeSkillService.chatWithFile(
                    sessionId, content, fileId, mimeType, skillName
            );

            Map<String, Object> result = new java.util.LinkedHashMap<>();
            result.put("sessionId", sessionId);
            result.put("reply", reply);
            if (fileId != null && !fileId.isBlank()) result.put("fileId", fileId);
            return ApiResult.ok(result);

        } catch (IllegalArgumentException e) {
            return ApiResult.fail("INVALID_INPUT", e.getMessage());
        } catch (Exception e) {
            return ApiResult.fail("CHAT_ERROR", e.getMessage());
        }
    }
}
