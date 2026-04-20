package com.claude.skill.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 一个完整的对话会话
 */
public class ConversationSession {

    private final String sessionId;
    private final String skillName;       // 绑定的 skill（可为 null 表示无 skill）
    private final List<ChatMessage> messages;
    private Instant createdAt;
    private Instant lastActiveAt;
    private int maxTurns;

    public ConversationSession(String skillName, int maxTurns) {
        this.sessionId = UUID.randomUUID().toString();
        this.skillName = skillName;
        this.messages = new ArrayList<>();
        this.createdAt = Instant.now();
        this.lastActiveAt = Instant.now();
        this.maxTurns = maxTurns;
    }

    public void addMessage(ChatMessage message) {
        messages.add(message);
        this.lastActiveAt = Instant.now();
        // 超出最大轮数时，保留 system 之后从最早的 user 消息开始裁剪
        if (messages.size() > maxTurns * 2) {
            messages.subList(0, 2).clear(); // 移除最早的一轮(user+assistant)
        }
    }

    /** 返回适合直接传给 Claude API 的 messages 列表副本 */
    public List<ChatMessage> getMessagesSnapshot() {
        return List.copyOf(messages);
    }

    public void clear() {
        messages.clear();
        this.lastActiveAt = Instant.now();
    }

    public String getSessionId() { return sessionId; }
    public String getSkillName() { return skillName; }
    public List<ChatMessage> getMessages() { return messages; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getLastActiveAt() { return lastActiveAt; }
    public int getMessageCount() { return messages.size(); }
}
