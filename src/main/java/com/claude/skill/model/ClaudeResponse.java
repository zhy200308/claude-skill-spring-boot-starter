package com.claude.skill.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * Claude API /v1/messages 响应体
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ClaudeResponse {

    private String id;
    private String type;
    private String role;
    private List<ContentBlock> content;

    @JsonProperty("stop_reason")
    private String stopReason;

    @JsonProperty("stop_sequence")
    private String stopSequence;

    private Usage usage;
    private String model;

    // ─── 内容块 ──────────────────────────────────────────────────
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ContentBlock {
        private String type;   // text / tool_use
        private String text;
        private String id;     // tool_use id
        private String name;   // tool name
        private Map<String, Object> input; // tool input

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getText() { return text; }
        public void setText(String text) { this.text = text; }
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public Map<String, Object> getInput() { return input; }
        public void setInput(Map<String, Object> input) { this.input = input; }

        public boolean isText() { return "text".equals(type); }
        public boolean isToolUse() { return "tool_use".equals(type); }
    }

    // ─── Token 用量 ───────────────────────────────────────────────
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Usage {
        @JsonProperty("input_tokens")
        private int inputTokens;
        @JsonProperty("output_tokens")
        private int outputTokens;

        public int getInputTokens() { return inputTokens; }
        public void setInputTokens(int inputTokens) { this.inputTokens = inputTokens; }
        public int getOutputTokens() { return outputTokens; }
        public void setOutputTokens(int outputTokens) { this.outputTokens = outputTokens; }
        public int total() { return inputTokens + outputTokens; }
    }

    /** 提取所有 text block 合并为字符串 */
    public String extractText() {
        if (content == null) return "";
        return content.stream()
            .filter(ContentBlock::isText)
            .map(ContentBlock::getText)
            .reduce("", (a, b) -> a + b);
    }

    /** 是否还需要执行 tool */
    public boolean needsToolCall() {
        return "tool_use".equals(stopReason);
    }

    /** 是否正常结束 */
    public boolean isFinished() {
        return "end_turn".equals(stopReason);
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public List<ContentBlock> getContent() { return content; }
    public void setContent(List<ContentBlock> content) { this.content = content; }
    public String getStopReason() { return stopReason; }
    public void setStopReason(String stopReason) { this.stopReason = stopReason; }
    public String getStopSequence() { return stopSequence; }
    public void setStopSequence(String stopSequence) { this.stopSequence = stopSequence; }
    public Usage getUsage() { return usage; }
    public void setUsage(Usage usage) { this.usage = usage; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
}
