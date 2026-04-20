package com.claude.skill.autoconfigure;

import com.claude.skill.config.ClaudeProperties;
import com.claude.skill.core.client.ClaudeClient;
import com.claude.skill.core.session.InMemorySessionStore;
import com.claude.skill.core.session.SessionStore;
import com.claude.skill.core.skill.SkillParser;
import com.claude.skill.core.skill.SkillRegistry;
import com.claude.skill.service.ClaudeSkillService;
import com.claude.skill.web.ChatController;
import com.claude.skill.web.SkillController;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Claude Skill Starter 自动配置
 *
 * 遵循"集成方优先"原则：所有 Bean 均使用 @ConditionalOnMissingBean，
 * 集成方只需声明同类型 Bean 即可覆盖默认实现。
 */
@AutoConfiguration
@EnableConfigurationProperties(ClaudeProperties.class)
public class ClaudeAutoConfiguration {

    private final ClaudeProperties props;

    public ClaudeAutoConfiguration(ClaudeProperties props) {
        this.props = props;
    }

    // ─── 基础组件 ─────────────────────────────────────────────────────────

    @Bean
    @ConditionalOnMissingBean
    public ObjectMapper claudeObjectMapper() {
        return new ObjectMapper();
    }

    @Bean
    @ConditionalOnMissingBean
    public ClaudeClient claudeClient(ObjectMapper objectMapper) {
        return new ClaudeClient(props, objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public SkillParser skillParser() {
        return new SkillParser();
    }

    @Bean
    @ConditionalOnMissingBean
    public SkillRegistry skillRegistry(SkillParser skillParser) {
        SkillRegistry registry = new SkillRegistry(skillParser, props);
        if (props.getSkill().isAutoLoad()) {
            registry.loadAll();
        }
        return registry;
    }

    // ─── 会话存储（默认内存，集成方可注入自定义实现）────────────────────────

    @Bean
    @ConditionalOnMissingBean(SessionStore.class)
    public SessionStore sessionStore() {
        return new InMemorySessionStore(props);
    }

    // ─── 核心服务 ─────────────────────────────────────────────────────────

    @Bean
    @ConditionalOnMissingBean
    public ClaudeSkillService claudeSkillService(ClaudeClient claudeClient,
                                                  SkillRegistry skillRegistry,
                                                  SessionStore sessionStore) {
        return new ClaudeSkillService(claudeClient, skillRegistry, sessionStore, props);
    }

    // ─── Web 端点（可通过 claude.web.enabled=false 关闭）──────────────────

    @Bean
    @ConditionalOnWebApplication
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "claude.web", name = "enabled", havingValue = "true", matchIfMissing = true)
    public SkillController skillController(SkillRegistry skillRegistry) {
        return new SkillController(skillRegistry);
    }

    @Bean
    @ConditionalOnWebApplication
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "claude.web", name = "enabled", havingValue = "true", matchIfMissing = true)
    public ChatController chatController(ClaudeSkillService claudeSkillService) {
        return new ChatController(claudeSkillService);
    }
}
