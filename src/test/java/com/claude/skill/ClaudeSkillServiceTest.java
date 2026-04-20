package com.claude.skill;

import com.claude.skill.config.ClaudeProperties;
import com.claude.skill.core.client.ClaudeClient;
import com.claude.skill.core.session.InMemorySessionStore;
import com.claude.skill.core.session.SessionStore;
import com.claude.skill.core.skill.SkillParser;
import com.claude.skill.core.skill.SkillRegistry;
import com.claude.skill.model.ClaudeResponse;
import com.claude.skill.model.ConversationSession;
import com.claude.skill.service.ClaudeSkillService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class ClaudeSkillServiceTest {

    private ClaudeSkillService service;
    private ClaudeClient mockClient;
    private SkillRegistry skillRegistry;
    private SessionStore sessionStore;
    private ClaudeProperties props;

    @BeforeEach
    void setUp() {
        props = new ClaudeProperties();
        props.setApiKey("test-key");

        mockClient    = Mockito.mock(ClaudeClient.class);
        skillRegistry = new SkillRegistry(new SkillParser(), props);
        sessionStore  = new InMemorySessionStore(props);
        service       = new ClaudeSkillService(mockClient, skillRegistry, sessionStore, props);
    }

    @Test
    void testCreateSession() {
        ConversationSession session = service.createSession(null);
        assertNotNull(session.getSessionId());
        assertEquals(0, session.getMessageCount());
    }

    @Test
    void testCreateSessionWithSkill() {
        ConversationSession session = service.createSession("ai-rewrite");
        assertEquals("ai-rewrite", session.getSkillName());
    }

    @Test
    void testChatAddsMessages() throws IOException, InterruptedException {
        // 模拟 Claude 返回正常响应
        ClaudeResponse mockResponse = buildTextResponse("Hello from Claude!");
        when(mockClient.send(any())).thenReturn(mockResponse);

        ConversationSession session = service.createSession(null);
        String reply = service.chat(session.getSessionId(), "Hello");

        assertEquals("Hello from Claude!", reply);
        // user + assistant = 2 条消息
        assertEquals(2, sessionStore.get(session.getSessionId())
            .map(ConversationSession::getMessageCount).orElse(0));
    }

    @Test
    void testSessionNotFound() {
        assertThrows(java.util.NoSuchElementException.class,
            () -> service.chat("non-existent-id", "Hello"));
    }

    @Test
    void testDeleteSession() {
        ConversationSession session = service.createSession(null);
        service.deleteSession(session.getSessionId());
        assertTrue(service.getSession(session.getSessionId()).isEmpty());
    }

    @Test
    void testClearSession() throws IOException, InterruptedException {
        ClaudeResponse mockResponse = buildTextResponse("Reply");
        when(mockClient.send(any())).thenReturn(mockResponse);

        ConversationSession session = service.createSession(null);
        service.chat(session.getSessionId(), "Hello");
        assertEquals(2, session.getMessageCount());

        service.clearSession(session.getSessionId());
        assertEquals(0, service.getSession(session.getSessionId())
            .map(ConversationSession::getMessageCount).orElse(-1));
    }

    @Test
    void testListSessions() {
        service.createSession(null);
        service.createSession(null);
        assertEquals(2, service.listSessionIds().size());
    }

    // ─── Helper ───────────────────────────────────────────────────────────

    private ClaudeResponse buildTextResponse(String text) {
        ClaudeResponse.ContentBlock block = new ClaudeResponse.ContentBlock();
        block.setType("text");
        block.setText(text);

        ClaudeResponse response = new ClaudeResponse();
        response.setContent(List.of(block));
        response.setStopReason("end_turn");
        return response;
    }
}
