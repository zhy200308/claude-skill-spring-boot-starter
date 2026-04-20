package com.claude.skill.core.session;

import com.claude.skill.model.ConversationSession;

import java.util.List;
import java.util.Optional;

/**
 * 会话存储接口
 * 集成方可实现此接口并注册为 Bean，替换默认的内存实现
 *
 * <pre>
 * {@code
 * @Bean
 * public SessionStore redisSessionStore(...) {
 *     return new RedisSessionStore(...);
 * }
 * }
 * </pre>
 */
public interface SessionStore {

    /** 创建并存储新会话 */
    ConversationSession create(String skillName);

    /** 根据 ID 获取会话 */
    Optional<ConversationSession> get(String sessionId);

    /** 保存/更新会话 */
    void save(ConversationSession session);

    /** 删除会话 */
    void delete(String sessionId);

    /** 清空所有会话 */
    void clear();

    /** 列出所有会话 ID（用于管理接口） */
    List<String> listSessionIds();

    /** 获取会话总数 */
    int count();
}
