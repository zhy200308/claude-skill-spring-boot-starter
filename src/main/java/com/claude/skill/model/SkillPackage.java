package com.claude.skill.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

// ─── Skill 包（解析 .skill 文件后的结果）───────────────────────────────
public class SkillPackage {
    private final String name;
    private final String description;
    private final String systemPrompt;   // SKILL.md正文 + references 合并内容
    private final Map<String, Object> inputSchema; // 自定义 tool schema（可选）

    public SkillPackage(String name, String description, String systemPrompt,
                        Map<String, Object> inputSchema) {
        this.name = name;
        this.description = description;
        this.systemPrompt = systemPrompt;
        this.inputSchema = inputSchema != null ? inputSchema : defaultSchema();
    }

    private static Map<String, Object> defaultSchema() {
        return Map.of(
            "type", "object",
            "properties", Map.of(
                "text", Map.of("type", "string", "description", "Input text to process")
            ),
            "required", List.of("text")
        );
    }

    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getSystemPrompt() { return systemPrompt; }
    public Map<String, Object> getInputSchema() { return inputSchema; }

    /** 转成 Claude API tools[] 中的一个 tool entry */
    public Map<String, Object> toToolDefinition() {
        return Map.of(
            "name", name,
            "description", description,
            "input_schema", inputSchema
        );
    }

    @Override
    public String toString() {
        return "SkillPackage{name='" + name + "', description='" +
               description.substring(0, Math.min(50, description.length())) + "...'}";
    }
}
