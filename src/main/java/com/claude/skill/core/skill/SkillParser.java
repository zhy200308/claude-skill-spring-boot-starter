package com.claude.skill.core.skill;

import com.claude.skill.model.SkillPackage;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 解析 .skill 文件（ZIP 格式）
 *
 * 文件结构:
 *   skill-name/
 *     SKILL.md           ← 含 YAML frontmatter（name/description）+ 指令正文
 *     references/        ← 可选，所有 .md 引用资料，会被合并进 systemPrompt
 *       *.md
 *     schema.json        ← 可选，自定义 Claude tool input_schema
 */
public class SkillParser {

    private static final Logger log = Logger.getLogger(SkillParser.class.getName());
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 从文件路径解析 */
    public SkillPackage parse(Path skillFile) throws IOException {
        try (InputStream is = Files.newInputStream(skillFile)) {
            return parse(is, skillFile.getFileName().toString());
        }
    }

    /** 从 InputStream 解析（支持上传场景） */
    public SkillPackage parse(InputStream inputStream, String filename) throws IOException {
        Map<String, String> files = new LinkedHashMap<>();

        try (ZipInputStream zis = new ZipInputStream(inputStream)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (!entry.isDirectory()) {
                    // 规范化路径：去掉顶层目录前缀，只保留相对路径
                    String name = normalizePath(entry.getName());
                    String content = new String(zis.readAllBytes(), StandardCharsets.UTF_8);
                    files.put(name, content);
                }
                zis.closeEntry();
            }
        }

        if (files.isEmpty()) {
            throw new IllegalArgumentException("Invalid .skill file: " + filename + " — ZIP is empty");
        }

        // 找 SKILL.md（忽略大小写，兼容不同打包方式）
        String skillMdContent = findSkillMd(files, filename);

        // 解析 YAML frontmatter
        FrontMatter meta = parseFrontmatter(skillMdContent);
        String mainBody   = stripFrontmatter(skillMdContent);

        // 合并 references
        StringBuilder systemPrompt = new StringBuilder(mainBody);
        files.entrySet().stream()
            .filter(e -> e.getKey().startsWith("references/") && e.getKey().endsWith(".md"))
            .sorted(Map.Entry.comparingByKey())
            .forEach(e -> systemPrompt
                .append("\n\n---\n# Reference: ").append(e.getKey()).append("\n")
                .append(e.getValue()));

        // 解析可选的 schema.json
        Map<String, Object> inputSchema = null;
        String schemaJson = files.get("schema.json");
        if (schemaJson != null && !schemaJson.isBlank()) {
            try {
                //noinspection unchecked
                inputSchema = objectMapper.readValue(schemaJson, Map.class);
                log.info("Loaded custom schema for skill: " + meta.name);
            } catch (Exception e) {
                log.warning("Failed to parse schema.json for skill: " + meta.name + " — " + e.getMessage());
            }
        }

        log.info("Parsed skill: " + meta.name);
        return new SkillPackage(meta.name, meta.description, systemPrompt.toString(), inputSchema);
    }

    // ─── 内部工具 ─────────────────────────────────────────────────────────

    private String findSkillMd(Map<String, String> files, String filename) {
        // 精确匹配
        if (files.containsKey("SKILL.md")) return files.get("SKILL.md");
        // 忽略大小写
        return files.entrySet().stream()
            .filter(e -> e.getKey().equalsIgnoreCase("skill.md"))
            .map(Map.Entry::getValue)
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException(
                "Invalid .skill file: " + filename + " — SKILL.md not found"));
    }

    /** 移除顶层目录前缀，如 "ai-rewrite/references/foo.md" → "references/foo.md" */
    private String normalizePath(String entryName) {
        int slash = entryName.indexOf('/');
        if (slash > 0 && slash < entryName.length() - 1) {
            return entryName.substring(slash + 1);
        }
        return entryName;
    }

    /**
     * 解析 YAML frontmatter (--- ... ---)
     * 支持:
     *   name: xxx
     *   description: single line
     *   description: >
     *     multi line
     *     continuation
     */
    private FrontMatter parseFrontmatter(String md) {
        String name = "";
        String description = "";

        if (!md.startsWith("---")) return new FrontMatter(name, description);
        int end = md.indexOf("---", 3);
        if (end < 0) return new FrontMatter(name, description);

        String yaml = md.substring(3, end).trim();
        String[] lines = yaml.split("\n");
        boolean inDescription = false;
        StringBuilder descBuilder = new StringBuilder();

        for (String line : lines) {
            if (line.startsWith("name:")) {
                name = line.substring(5).trim();
                inDescription = false;
            } else if (line.startsWith("description:")) {
                String val = line.substring(12).trim();
                if (val.equals(">") || val.isEmpty()) {
                    inDescription = true; // 多行折叠块
                } else {
                    descBuilder.append(val.replace("'", "").replace("\"", ""));
                    inDescription = false;
                }
            } else if (inDescription && (line.startsWith("  ") || line.startsWith("\t"))) {
                descBuilder.append(" ").append(line.trim());
            } else {
                inDescription = false;
            }
        }

        description = descBuilder.toString().trim();
        if (name.isEmpty()) {
            throw new IllegalArgumentException("SKILL.md frontmatter missing 'name' field");
        }
        return new FrontMatter(name, description);
    }

    private String stripFrontmatter(String md) {
        if (!md.startsWith("---")) return md;
        int end = md.indexOf("---", 3);
        if (end < 0) return md;
        return md.substring(end + 3).trim();
    }

    private record FrontMatter(String name, String description) {}
}
