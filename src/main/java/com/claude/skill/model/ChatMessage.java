package com.claude.skill.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// ─── 单条消息 ────────────────────────────────────────────────────────────
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChatMessage {
    private String role;   // user / assistant
    private Object content; // String 或 List<Map> (tool_result等)

    public ChatMessage() {}
    public ChatMessage(String role, String content) {
        this.role = role;
        this.content = content;
    }
    public ChatMessage(String role, Object content) {
        this.role = role;
        this.content = content;
    }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public Object getContent() { return content; }
    public void setContent(Object content) { this.content = content; }
}
