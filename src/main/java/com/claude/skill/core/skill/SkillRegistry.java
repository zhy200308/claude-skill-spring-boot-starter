package com.claude.skill.core.skill;

import com.claude.skill.config.ClaudeProperties;
import com.claude.skill.model.SkillPackage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Skill 注册中心
 * 负责：扫描加载 / 动态注册 / 热删除 / 构建 Claude tools[]
 */
public class SkillRegistry {

    private static final Logger log = Logger.getLogger(SkillRegistry.class.getName());

    private final Map<String, SkillPackage> registry = new ConcurrentHashMap<>();
    private final SkillParser parser;
    private final ClaudeProperties props;

    public SkillRegistry(SkillParser parser, ClaudeProperties props) {
        this.parser = parser;
        this.props  = props;
    }

    // ─── 初始化扫描 ───────────────────────────────────────────────────────

    /** 启动时扫描 skill.dir 目录，加载所有 .skill 文件 */
    public void loadAll() {
        Path skillDir = ensureSkillDir();
        try (var stream = Files.list(skillDir)) {
            stream.filter(p -> p.toString().endsWith(".skill"))
                  .forEach(p -> {
                      try {
                          SkillPackage skill = parser.parse(p);
                          registry.put(skill.getName(), skill);
                          log.info("✅ Loaded skill: " + skill.getName() + " from " + p.getFileName());
                      } catch (Exception e) {
                          log.warning("❌ Failed to load skill: " + p.getFileName() + " — " + e.getMessage());
                      }
                  });
        } catch (IOException e) {
            log.warning("Failed to scan skill directory: " + e.getMessage());
        }
        log.info("Skill registry initialized with " + registry.size() + " skills.");
    }

    // ─── 动态管理 ─────────────────────────────────────────────────────────

    /** 从 InputStream 注册（用于 HTTP 上传接口） */
    public SkillPackage register(InputStream is, String filename) throws IOException {
        String safeName = normalizeFilename(filename);
        Path skillFile = ensureSkillDir().resolve(safeName);
        Files.copy(is, skillFile, StandardCopyOption.REPLACE_EXISTING);
        SkillPackage skill = parser.parse(skillFile);
        registry.put(skill.getName(), skill);
        log.info("Registered skill via upload: " + skill.getName() + ", saved to " + skillFile.toAbsolutePath());
        return skill;
    }

    /** 从本地文件路径注册 */
    public SkillPackage register(Path skillFile) throws IOException {
        SkillPackage skill = parser.parse(skillFile);
        registry.put(skill.getName(), skill);
        log.info("Registered skill from path: " + skill.getName());
        return skill;
    }

    /** 删除 skill */
    public boolean remove(String skillName) {
        SkillPackage removed = registry.remove(skillName);
        if (removed != null) {
            log.info("Removed skill: " + skillName);
            return true;
        }
        return false;
    }

    /** 重新加载某个 skill（热更新） */
    public SkillPackage reload(String skillName) throws IOException {
        Path skillFile = ensureSkillDir().resolve(skillName + ".skill");
        if (!Files.exists(skillFile)) {
            throw new NoSuchElementException("Skill file not found: " + skillFile);
        }
        return register(skillFile);
    }

    private Path ensureSkillDir() {
        try {
            Path skillDir = resolveSkillDir();
            Files.createDirectories(skillDir);
            return skillDir;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to initialize skill directory", e);
        }
    }

    private Path resolveSkillDir() {
        String configured = props.getSkill().getDir();
        String dir = (configured == null || configured.isBlank()) ? "skills" : configured.trim();
        Path path = Path.of(dir);
        if (path.isAbsolute()) {
            return path.normalize();
        }
        Path resourceDir = Path.of(System.getProperty("user.dir"), "src", "main", "resources");
        return resourceDir.resolve(path).normalize();
    }

    private String normalizeFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            throw new IllegalArgumentException("Invalid skill filename");
        }
        String name = Path.of(filename).getFileName().toString();
        if (name.isBlank()) {
            throw new IllegalArgumentException("Invalid skill filename");
        }
        return name;
    }

    // ─── 查询 ─────────────────────────────────────────────────────────────

    public Optional<SkillPackage> get(String skillName) {
        return Optional.ofNullable(registry.get(skillName));
    }

    public boolean exists(String skillName) {
        return registry.containsKey(skillName);
    }

    public Collection<SkillPackage> all() {
        return Collections.unmodifiableCollection(registry.values());
    }

    public int count() {
        return registry.size();
    }

    // ─── 组装 Claude tools[] ──────────────────────────────────────────────

    /** 将所有 skill 转为 Claude API tools 参数 */
    public List<Map<String, Object>> buildToolsForAll() {
        return registry.values().stream()
            .map(SkillPackage::toToolDefinition)
            .collect(Collectors.toList());
    }

    /** 将指定 skill 列表转为 Claude API tools 参数 */
    public List<Map<String, Object>> buildTools(List<String> skillNames) {
        return skillNames.stream()
            .map(registry::get)
            .filter(Objects::nonNull)
            .map(SkillPackage::toToolDefinition)
            .collect(Collectors.toList());
    }

    /** 获取某个 skill 的 system prompt */
    public Optional<String> getSystemPrompt(String skillName) {
        return get(skillName).map(SkillPackage::getSystemPrompt);
    }
}
