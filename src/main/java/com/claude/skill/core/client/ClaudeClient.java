package com.claude.skill.core.client;

import com.claude.skill.config.ClaudeProperties;
import com.claude.skill.model.ClaudeResponse;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.*;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.logging.Logger;

/**
 * Claude API HTTP 客户端
 *
 * 代理策略（claude.proxy.mode）：
 *   SYSTEM  → 读取 JVM 系统代理（http.proxyHost 或 HTTPS_PROXY 环境变量）
 *   CUSTOM  → 使用 claude.proxy.host/port
 *   NONE    → 强制直连
 */
public class ClaudeClient {

    private static final Logger log = Logger.getLogger(ClaudeClient.class.getName());

    private final ClaudeProperties props;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public ClaudeClient(ClaudeProperties props, ObjectMapper objectMapper) {
        this.props        = props;
        this.objectMapper = objectMapper;
        this.httpClient   = buildHttpClient(props);
    }

    // ─── 核心调用 ─────────────────────────────────────────────────────────

    /**
     * 发送请求到 Claude API
     * @param requestBody 完整的请求体 Map（调用方负责组装）
     * @return ClaudeResponse
     */
    public ClaudeResponse send(Map<String, Object> requestBody) throws IOException, InterruptedException {
        String json = objectMapper.writeValueAsString(requestBody);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(props.getApiUrl()))
            .header("Content-Type", "application/json")
            .header("x-api-key", props.getApiKey())
            .header("anthropic-version", props.getAnthropicVersion())
            .timeout(Duration.ofSeconds(props.getTimeoutSeconds()))
            .POST(HttpRequest.BodyPublishers.ofString(json))
            .build();

        log.fine("Calling Claude API: " + props.getApiUrl());

        HttpResponse<String> response = httpClient.send(
            request, HttpResponse.BodyHandlers.ofString()
        );

        if (response.statusCode() != 200) {
            throw new ClaudeApiException(response.statusCode(), response.body());
        }

        return objectMapper.readValue(response.body(), ClaudeResponse.class);
    }

    // ─── 代理构建 ─────────────────────────────────────────────────────────

    private HttpClient buildHttpClient(ClaudeProperties props) {
        HttpClient.Builder builder = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(props.getTimeoutSeconds()));

        ClaudeProperties.Proxy proxyCfg = props.getProxy();

        switch (proxyCfg.getMode()) {
            case NONE -> {
                // 强制直连：覆盖任何系统代理
                builder.proxy(ProxySelector.of(null));
                log.info("[Claude] Proxy: NONE (direct connection)");
            }
            case CUSTOM -> {
                // 用户自定义代理
                if (proxyCfg.getHost() == null || proxyCfg.getHost().isBlank()) {
                    log.warning("[Claude] Proxy mode is CUSTOM but proxy.host is not set, falling back to SYSTEM");
                    builder.proxy(ProxySelector.getDefault());
                } else {
                    Proxy.Type type = "SOCKS5".equalsIgnoreCase(proxyCfg.getType())
                        ? Proxy.Type.SOCKS : Proxy.Type.HTTP;
                    InetSocketAddress addr = new InetSocketAddress(proxyCfg.getHost(), proxyCfg.getPort());
                    ProxySelector selector = ProxySelector.of(addr);

                    // 若有认证信息，注册全局 Authenticator
                    if (proxyCfg.getUsername() != null && !proxyCfg.getUsername().isBlank()) {
                        Authenticator.setDefault(new Authenticator() {
                            @Override
                            protected PasswordAuthentication getPasswordAuthentication() {
                                return new PasswordAuthentication(
                                    proxyCfg.getUsername(),
                                    proxyCfg.getPassword() != null
                                        ? proxyCfg.getPassword().toCharArray()
                                        : new char[0]
                                );
                            }
                        });
                    }
                    builder.proxy(selector);
                    log.info("[Claude] Proxy: CUSTOM " + type + " → " + proxyCfg.getHost() + ":" + proxyCfg.getPort());
                }
            }
            default -> {
                // SYSTEM（默认）：使用系统代理
                // 1. 先尝试 JVM 系统属性（http.proxyHost / https.proxyHost）
                // 2. 再尝试 HTTPS_PROXY / HTTP_PROXY 环境变量
                ProxySelector systemProxy = resolveSystemProxy();
                builder.proxy(systemProxy);
                log.info("[Claude] Proxy: SYSTEM → " + describeProxy(systemProxy));
            }
        }

        return builder.build();
    }

    /**
     * 解析系统代理：
     *  优先 JVM 属性（-Dhttps.proxyHost=...），
     *  其次 HTTPS_PROXY / HTTP_PROXY 环境变量
     */
    private ProxySelector resolveSystemProxy() {
        // 检查 JVM 属性
        String jvmHost = System.getProperty("https.proxyHost",
                         System.getProperty("http.proxyHost", ""));
        if (!jvmHost.isBlank()) {
            int port = Integer.parseInt(
                System.getProperty("https.proxyPort",
                System.getProperty("http.proxyPort", "8080"))
            );
            log.info("[Claude] Using JVM system proxy: " + jvmHost + ":" + port);
            return ProxySelector.of(new InetSocketAddress(jvmHost, port));
        }

        // 检查环境变量 HTTPS_PROXY / HTTP_PROXY
        String envProxy = Optional.ofNullable(System.getenv("HTTPS_PROXY"))
                           .or(() -> Optional.ofNullable(System.getenv("https_proxy")))
                           .or(() -> Optional.ofNullable(System.getenv("HTTP_PROXY")))
                           .or(() -> Optional.ofNullable(System.getenv("http_proxy")))
                           .orElse("");
        if (!envProxy.isBlank()) {
            try {
                URI uri = URI.create(envProxy.startsWith("http") ? envProxy : "http://" + envProxy);
                int port = uri.getPort() > 0 ? uri.getPort() : 8080;
                log.info("[Claude] Using ENV proxy: " + uri.getHost() + ":" + port);
                return ProxySelector.of(new InetSocketAddress(uri.getHost(), port));
            } catch (Exception e) {
                log.warning("[Claude] Failed to parse ENV proxy: " + envProxy);
            }
        }

        // 无系统代理，使用默认（直连）
        return ProxySelector.getDefault();
    }

    private String describeProxy(ProxySelector selector) {
        try {
            List<Proxy> proxies = selector.select(URI.create("https://api.anthropic.com"));
            if (proxies.isEmpty() || proxies.get(0).type() == Proxy.Type.DIRECT) {
                return "direct (no system proxy detected)";
            }
            return proxies.get(0).address().toString();
        } catch (Exception e) {
            return "unknown";
        }
    }

    // ─── 异常 ─────────────────────────────────────────────────────────────

    public static class ClaudeApiException extends IOException {
        private final int statusCode;
        private final String responseBody;

        public ClaudeApiException(int statusCode, String responseBody) {
            super("Claude API error " + statusCode + ": " + responseBody);
            this.statusCode   = statusCode;
            this.responseBody = responseBody;
        }

        public int getStatusCode()   { return statusCode;    }
        public String getResponseBody() { return responseBody; }
    }
}
