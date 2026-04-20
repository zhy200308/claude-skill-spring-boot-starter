package com.claude.skill.web;

import com.claude.skill.core.skill.SkillRegistry;
import com.claude.skill.model.ApiResult;
import com.claude.skill.model.SkillPackage;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Skill 管理接口
 * 默认路径前缀由 ClaudeProperties.web.basePath 控制（默认 /claude）
 *
 * POST   /claude/skills/upload     — 上传 .skill 文件
 * GET    /claude/skills            — 列出所有 skill
 * GET    /claude/skills/{name}     — 查看 skill 信息
 * DELETE /claude/skills/{name}     — 删除 skill
 * POST   /claude/skills/{name}/reload — 从磁盘热重载
 */
@RestController
public class SkillController {

    private final SkillRegistry skillRegistry;

    public SkillController(SkillRegistry skillRegistry) {
        this.skillRegistry = skillRegistry;
    }

    /** 上传 .skill 文件并注册 */
    @PostMapping(value = "${claude.web.base-path:/claude}/skills/upload",
                 consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResult<Map<String, String>> upload(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ApiResult.fail("FILE_EMPTY", "Uploaded file is empty");
        }
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || !originalFilename.endsWith(".skill")) {
            return ApiResult.fail("INVALID_FORMAT", "Only .skill files are accepted");
        }
        try {
            SkillPackage skill = skillRegistry.register(
                file.getInputStream(), originalFilename
            );
            return ApiResult.ok("Skill registered", Map.of(
                "name",        skill.getName(),
                "description", skill.getDescription()
            ));
        } catch (Exception e) {
            return ApiResult.fail("PARSE_ERROR", "Failed to parse skill: " + e.getMessage());
        }
    }

    /** 列出所有已注册 skill */
    @GetMapping("${claude.web.base-path:/claude}/skills")
    public ApiResult<List<Map<String, String>>> list() {
        List<Map<String, String>> skills = skillRegistry.all().stream()
            .map(s -> Map.of(
                "name",        s.getName(),
                "description", s.getDescription().substring(
                    0, Math.min(100, s.getDescription().length())) + "..."
            ))
            .collect(Collectors.toList());
        return ApiResult.ok(skills);
    }

    /** 获取单个 skill 详情 */
    @GetMapping("${claude.web.base-path:/claude}/skills/{name}")
    public ApiResult<Map<String, Object>> get(@PathVariable String name) {
        return skillRegistry.get(name)
            .map(s -> ApiResult.ok(Map.<String, Object>of(
                "name",         s.getName(),
                "description",  s.getDescription(),
                "inputSchema",  s.getInputSchema()
            )))
            .orElse(ApiResult.fail("NOT_FOUND", "Skill not found: " + name));
    }

    /** 删除 skill */
    @DeleteMapping("${claude.web.base-path:/claude}/skills/{name}")
    public ApiResult<Void> delete(@PathVariable String name) {
        boolean removed = skillRegistry.remove(name);
        if (removed) return ApiResult.ok(null);
        return ApiResult.fail("NOT_FOUND", "Skill not found: " + name);
    }

    /** 从磁盘热重载 skill */
    @PostMapping("${claude.web.base-path:/claude}/skills/{name}/reload")
    public ApiResult<Map<String, String>> reload(@PathVariable String name) {
        try {
            SkillPackage skill = skillRegistry.reload(name);
            return ApiResult.ok("Skill reloaded", Map.of("name", skill.getName()));
        } catch (Exception e) {
            return ApiResult.fail("RELOAD_ERROR", e.getMessage());
        }
    }
}
