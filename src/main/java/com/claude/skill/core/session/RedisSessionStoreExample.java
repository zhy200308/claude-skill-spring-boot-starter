package com.claude.skill.core.session;

/**
 * Redis 会话存储示例（供集成方参考）
 *
 * 集成方在自己的项目中实现此类，然后声明为 Bean 即可替换默认内存存储：
 *
 *   @Bean
 *   public SessionStore sessionStore(RedisTemplate<String, Object> redis,
 *                                    ClaudeProperties props) {
 *       return new RedisSessionStore(redis, props);
 *   }
 *
 * 依赖（集成方自行添加）：
 *   <dependency>
 *       <groupId>org.springframework.boot</groupId>
 *       <artifactId>spring-boot-starter-data-redis</artifactId>
 *   </dependency>
 *
 * ─────────────────────────────────────────────────────────────────────────
 * 下方为伪代码示例，实际使用请取消注释并补充 RedisTemplate 引用
 * ─────────────────────────────────────────────────────────────────────────
 */

/*
import com.claude.skill.config.ClaudeProperties;
import com.claude.skill.model.ConversationSession;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

public class RedisSessionStore implements SessionStore {

    private static final String KEY_PREFIX = "claude:session:";

    private final RedisTemplate<String, Object> redis;
    private final Duration ttl;
    private final int maxTurns;

    public RedisSessionStore(RedisTemplate<String, Object> redis, ClaudeProperties props) {
        this.redis    = redis;
        this.ttl      = Duration.ofMinutes(props.getSession().getTtlMinutes());
        this.maxTurns = props.getSession().getMaxTurnsPerSession();
    }

    @Override
    public ConversationSession create(String skillName) {
        ConversationSession session = new ConversationSession(skillName, maxTurns);
        save(session);
        return session;
    }

    @Override
    public Optional<ConversationSession> get(String sessionId) {
        Object val = redis.opsForValue().get(KEY_PREFIX + sessionId);
        return Optional.ofNullable((ConversationSession) val);
    }

    @Override
    public void save(ConversationSession session) {
        redis.opsForValue().set(
            KEY_PREFIX + session.getSessionId(), session, ttl
        );
    }

    @Override
    public void delete(String sessionId) {
        redis.delete(KEY_PREFIX + sessionId);
    }

    @Override
    public void clear() {
        // 生产环境慎用 keys()，建议用 scan
        redis.keys(KEY_PREFIX + "*").forEach(redis::delete);
    }

    @Override
    public List<String> listSessionIds() {
        return redis.keys(KEY_PREFIX + "*").stream()
            .map(k -> k.replace(KEY_PREFIX, ""))
            .toList();
    }

    @Override
    public int count() {
        return listSessionIds().size();
    }
}
*/

// 此文件为注释示例，无需编译，集成方参考实现即可
public class RedisSessionStoreExample {}
