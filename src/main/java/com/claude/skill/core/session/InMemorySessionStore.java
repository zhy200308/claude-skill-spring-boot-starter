package com.claude.skill.core.session;

import com.claude.skill.config.ClaudeProperties;
import com.claude.skill.model.ConversationSession;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 默认内存会话存储（LRU 淘汰 + TTL 过期）
 */
public class InMemorySessionStore implements SessionStore {

    private final Map<String, ConversationSession> store;
    private final int maxSize;
    private final long ttlMillis;
    private final int maxTurns;

    public InMemorySessionStore(ClaudeProperties props) {
        this.maxSize   = props.getSession().getMaxSize();
        this.ttlMillis = props.getSession().getTtlMinutes() * 60_000L;
        this.maxTurns  = props.getSession().getMaxTurnsPerSession();

        // LinkedHashMap 实现 LRU：accessOrder=true，超出 maxSize 时移除最旧的
        this.store = Collections.synchronizedMap(
            new LinkedHashMap<>(maxSize, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, ConversationSession> eldest) {
                    return size() > maxSize;
                }
            }
        );
    }

    @Override
    public ConversationSession create(String skillName) {
        evictExpired();
        ConversationSession session = new ConversationSession(skillName, maxTurns);
        store.put(session.getSessionId(), session);
        return session;
    }

    @Override
    public Optional<ConversationSession> get(String sessionId) {
        ConversationSession session = store.get(sessionId);
        if (session == null) return Optional.empty();
        if (isExpired(session)) {
            store.remove(sessionId);
            return Optional.empty();
        }
        return Optional.of(session);
    }

    @Override
    public void save(ConversationSession session) {
        store.put(session.getSessionId(), session);
    }

    @Override
    public void delete(String sessionId) {
        store.remove(sessionId);
    }

    @Override
    public void clear() {
        store.clear();
    }

    @Override
    public List<String> listSessionIds() {
        evictExpired();
        return new ArrayList<>(store.keySet());
    }

    @Override
    public int count() {
        evictExpired();
        return store.size();
    }

    private boolean isExpired(ConversationSession session) {
        if (ttlMillis <= 0) return false;
        return session.getLastActiveAt().plusMillis(ttlMillis).isBefore(Instant.now());
    }

    private void evictExpired() {
        if (ttlMillis <= 0) return;
        store.entrySet().removeIf(e -> isExpired(e.getValue()));
    }
}
