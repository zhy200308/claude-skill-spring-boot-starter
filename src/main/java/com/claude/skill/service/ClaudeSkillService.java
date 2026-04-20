package com.claude.skill.service;

import com.claude.skill.config.ClaudeProperties;
import com.claude.skill.core.client.ClaudeClient;
import com.claude.skill.core.session.SessionStore;
import com.claude.skill.core.skill.SkillRegistry;
import com.claude.skill.model.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;
import java.util.logging.Logger;

/**
 * Claude Skill 核心服务
 *
 * 提供以下调用模式：
 *   1. executeSkill()          — 无会话，单次调用指定 skill
 *   2. chat()                  — 多轮会话，不绑定 skill
 *   3. chatWithSkill()         — 多轮会话 + 绑定 skill 的 system prompt
 *   4. chatWithFile()          — 携带文件（PDF/图片）的多轮会话，skill 可选
 *   5. uploadFile()            — 上传文件到 Files API，返回 file_id
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

    public String executeSkill(String skillName, String userText) throws IOException, InterruptedException {
        SkillPackage skill = skillRegistry.get(skillName)
                .orElseThrow(() -> new NoSuchElementException("Skill not found: " + skillName));

        Map<String, Object> requestBody = buildRequest(
                skill.getSystemPrompt(),
                List.of(new ChatMessage("user", userText)),
                null, null
        );

        ClaudeResponse response = client.send(requestBody);
        return response.extractText();
    }

    // ─── 模式2：多轮会话（不绑定 skill）────────────────────────────────────

    public ConversationSession createSession(String skillName) {
        return sessionStore.create(skillName);
    }

    public String chat(String sessionId, String userMessage) throws IOException, InterruptedException {
        ConversationSession session = sessionStore.get(sessionId)
                .orElseThrow(() -> new NoSuchElementException("Session not found: " + sessionId));
        return chatInternal(session, userMessage, false, null, false);
    }

    // ─── 模式3：多轮会话 + Skill system prompt ────────────────────────────

    public String chatWithSkill(String sessionId, String userMessage) throws IOException, InterruptedException {
        return chatWithSkill(sessionId, userMessage, null);
    }

    public String chatWithSkill(String sessionId, String userMessage, String skillName)
            throws IOException, InterruptedException {
        ConversationSession session = sessionStore.get(sessionId)
                .orElseThrow(() -> new NoSuchElementException("Session not found: " + sessionId));
        return chatInternal(session, userMessage, true, skillName, false);
    }

    // ─── 模式4：携带文件的多轮会话 ────────────────────────────────────────

    /**
     * 上传文件到 Anthropic Files API
     *
     * @param file Spring MultipartFile
     * @return file_id（后续在 chatWithFile 中引用）
     */
    public String uploadFile(MultipartFile file) throws IOException, InterruptedException {
        ClaudeClient.FileUploadResult result = client.uploadFile(file);
        return result.getFileId();
    }

    /**
     * 上传文件并返回完整元信息
     */
    public ClaudeClient.FileUploadResult uploadFileWithMeta(MultipartFile file) throws IOException, InterruptedException {
        return client.uploadFile(file);
    }

    /**
     * 携带文件的多轮对话（统一入口）
     *
     * 覆盖 4 种组合：
     *   skill=null,   fileId=null   → 普通对话
     *   skill!=null,  fileId=null   → skill 对话
     *   skill=null,   fileId!=null  → 文件对话
     *   skill!=null,  fileId!=null  → skill + 文件对话
     *
     * @param sessionId 会话 ID
     * @param userMessage 用户文本（可与文件同时传入）
     * @param fileId 已上传文件的 file_id；null 表示无文件
     * @param mimeType 文件 MIME type（用于决定 document/image 块）；fileId 非空时必填
     * @param skillName 显式指定 skill；null 表示沿用会话绑定的 skill 或不使用 skill
     */
    public String chatWithFile(String sessionId,
                               String userMessage,
                               String fileId,
                               String mimeType,
                               String skillName) throws IOException, InterruptedException {
        ConversationSession session = sessionStore.get(sessionId)
                .orElseThrow(() -> new NoSuchElementException("Session not found: " + sessionId));

        // 决定是否注入 skill system prompt
        String effectiveSkill = (skillName != null && !skillName.isBlank())
                ? skillName
                : session.getSkillName();
        boolean useSkill = effectiveSkill != null && !effectiveSkill.isBlank();

        // 构造多模态 user content
        Object userContent;
        boolean hasFile = fileId != null && !fileId.isBlank();
        if (hasFile) {
            userContent = buildMultimodalContent(userMessage, fileId, mimeType);
        } else {
            userContent = userMessage != null ? userMessage : "";
        }

        // 直接插入会话历史
        session.addMessage(new ChatMessage("user", userContent));

        // 组装 system prompt
        String systemPrompt = null;
        if (useSkill) {
            systemPrompt = skillRegistry.getSystemPrompt(effectiveSkill)
                    .orElseThrow(() -> new NoSuchElementException("Skill not found: " + effectiveSkill));
        }

        // 带 beta header 调用
        Map<String, String> extraHeaders = hasFile
                ? Map.of("anthropic-beta", ClaudeClient.FILES_API_BETA)
                : null;

        String finalText = "";
        for (int round = 0; round < 5; round++) {
            Map<String, Object> requestBody = buildRequest(
                    systemPrompt,
                    session.getMessagesSnapshot(),
                    null, null
            );

            ClaudeResponse response = client.send(requestBody, extraHeaders);

            if (response.isFinished()) {
                finalText = response.extractText();
                session.addMessage(new ChatMessage("assistant", finalText));
                break;
            }

            if (response.needsToolCall()) {
                session.addMessage(new ChatMessage("assistant", response.getContent()));
                List<Map<String, Object>> toolResults = processToolCalls(response);
                session.addMessage(new ChatMessage("user", toolResults));
            } else {
                finalText = response.extractText();
                session.addMessage(new ChatMessage("assistant", finalText));
                break;
            }
        }

        sessionStore.save(session);
        return finalText;
    }

    /**
     * 构造多模态 user content：[{text}, {document|image}]
     *
     * 支持的 MIME type：
     *   - application/pdf                              → document 块
     *   - image/jpeg, image/png, image/gif, image/webp → image 块
     *   其他类型会抛异常，建议先转为纯文本或 PDF
     */
    private List<Map<String, Object>> buildMultimodalContent(String userText, String fileId, String mimeType) {
        List<Map<String, Object>> content = new ArrayList<>();

        // 文本块（即使为空也保留，保证 Claude 知道用户意图）
        if (userText != null && !userText.isBlank()) {
            content.add(Map.of("type", "text", "text", userText));
        } else {
            content.add(Map.of("type", "text", "text", "请分析这份文件。"));
        }

        // 文件块
        String fileBlockType = resolveFileBlockType(mimeType);
        Map<String, Object> fileBlock = new LinkedHashMap<>();
        fileBlock.put("type", fileBlockType);
        fileBlock.put("source", Map.of("type", "file", "file_id", fileId));
        content.add(fileBlock);

        return content;
    }

    /**
     * 根据 MIME type 返回 Claude content block 类型
     */
    private String resolveFileBlockType(String mimeType) {
        if (mimeType == null || mimeType.isBlank()) {
            throw new IllegalArgumentException(
                    "mimeType is required when fileId is provided");
        }
        String m = mimeType.toLowerCase(Locale.ROOT);
        if (m.startsWith("image/")) {
            // 仅 jpeg/png/gif/webp 被支持
            if (m.equals("image/jpeg") || m.equals("image/png")
                    || m.equals("image/gif")  || m.equals("image/webp")) {
                return "image";
            }
            throw new IllegalArgumentException(
                    "Unsupported image type: " + mimeType + " (supported: jpeg/png/gif/webp)");
        }
        if (m.equals("application/pdf")) {
            return "document";
        }
        // 其余纯文本类文件（txt/md/csv/docx/xlsx）Anthropic 建议直接转文本内联
        throw new IllegalArgumentException(
                "Unsupported file type for file_id reference: " + mimeType +
                        " — convert to text and pass inline, or convert to PDF.");
    }

    // ─── 内部：多轮对话核心（复用） ──────────────────────────────────────

    private String chatInternal(ConversationSession session,
                                String userMessage,
                                boolean useSkillSystem,
                                String explicitSkillName,
                                boolean withFilesBeta)
            throws IOException, InterruptedException {

        session.addMessage(new ChatMessage("user", userMessage));

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

        Map<String, String> extraHeaders = withFilesBeta
                ? Map.of("anthropic-beta", ClaudeClient.FILES_API_BETA)
                : null;

        String finalText = "";

        for (int round = 0; round < 5; round++) {
            Map<String, Object> requestBody = buildRequest(
                    systemPrompt,
                    session.getMessagesSnapshot(),
                    null, null
            );

            ClaudeResponse response = client.send(requestBody, extraHeaders);

            if (response.isFinished()) {
                finalText = response.extractText();
                session.addMessage(new ChatMessage("assistant", finalText));
                break;
            }

            if (response.needsToolCall()) {
                session.addMessage(new ChatMessage("assistant", response.getContent()));
                List<Map<String, Object>> toolResults = processToolCalls(response);
                session.addMessage(new ChatMessage("user", toolResults));
            } else {
                finalText = response.extractText();
                session.addMessage(new ChatMessage("assistant", finalText));
                break;
            }
        }

        sessionStore.save(session);
        return finalText;
    }

    // ─── Tool 调用处理（框架预留） ────────────────────────────────────────

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
