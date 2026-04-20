package com.claude.skill.core.client;

import com.claude.skill.config.ClaudeProperties;
import com.claude.skill.model.ClaudeResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.*;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
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

    /** Files API beta 标记，上传及引用 file_id 的 messages 调用均需携带 */
    public static final String FILES_API_BETA = "files-api-2025-04-14";

    /** Files API 上传端点路径 */
    private static final String FILES_ENDPOINT_PATH = "/v1/files";

    private final ClaudeProperties props;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public ClaudeClient(ClaudeProperties props, ObjectMapper objectMapper) {
        this.props        = props;
        this.objectMapper = objectMapper;
        this.httpClient   = buildHttpClient(props);
    }

    // ─── 核心调用：messages ──────────────────────────────────────────────

    /**
     * 发送请求到 Claude messages API
     */
    public ClaudeResponse send(Map<String, Object> requestBody) throws IOException, InterruptedException {
        return send(requestBody, null);
    }

    /**
     * 发送请求到 Claude messages API，可附加额外 header（例如 anthropic-beta）
     */
    public ClaudeResponse send(Map<String, Object> requestBody, Map<String, String> extraHeaders)
            throws IOException, InterruptedException {
        String json = objectMapper.writeValueAsString(requestBody);

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(props.getApiUrl()))
                .header("Content-Type", "application/json")
                .header("x-api-key", props.getApiKey())
                .header("anthropic-version", props.getAnthropicVersion())
                .timeout(Duration.ofSeconds(props.getTimeoutSeconds()))
                .POST(HttpRequest.BodyPublishers.ofString(json));

        if (extraHeaders != null) {
            extraHeaders.forEach(builder::header);
        }

        log.fine("Calling Claude API: " + props.getApiUrl());

        HttpResponse<String> response = httpClient.send(
                builder.build(), HttpResponse.BodyHandlers.ofString()
        );

        if (response.statusCode() != 200) {
            throw new ClaudeApiException(response.statusCode(), response.body());
        }

        return objectMapper.readValue(response.body(), ClaudeResponse.class);
    }

    // ─── Files API：上传文件 ──────────────────────────────────────────────

    /**
     * 上传文件到 Anthropic Files API，返回 file_id
     *
     * 端点: POST {baseUrl}/v1/files
     * 必需 header: x-api-key, anthropic-version, anthropic-beta: files-api-2025-04-14
     * 请求体: multipart/form-data, 字段名 "file"
     *
     * 响应示例:
     * { "id": "file_011CNha8iCJcU1wXNR6q4V8w",
     *   "type": "file",
     *   "filename": "document.pdf",
     *   "mime_type": "application/pdf",
     *   "size_bytes": 1024000,
     *   "created_at": "2025-01-01T00:00:00Z",
     *   "downloadable": false }
     */
    public FileUploadResult uploadFile(MultipartFile file) throws IOException, InterruptedException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("file is null or empty");
        }

        String filename    = Optional.ofNullable(file.getOriginalFilename()).orElse("upload.bin");
        String contentType = Optional.ofNullable(file.getContentType()).orElse("application/octet-stream");
        byte[] fileBytes   = file.getBytes();

        String boundary = "----ClaudeBoundary" + UUID.randomUUID().toString().replace("-", "");
        byte[] body = buildMultipartBody(boundary, "file", filename, contentType, fileBytes);

        URI uploadUri = URI.create(resolveFilesEndpoint());

        HttpRequest request = HttpRequest.newBuilder()
                .uri(uploadUri)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .header("x-api-key", props.getApiKey())
                .header("anthropic-version", props.getAnthropicVersion())
                .header("anthropic-beta", FILES_API_BETA)
                .timeout(Duration.ofSeconds(props.getTimeoutSeconds()))
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();

        log.info("[Claude] Uploading file: " + filename + " (" + fileBytes.length + " bytes, " + contentType + ")");

        HttpResponse<String> response = httpClient.send(
                request, HttpResponse.BodyHandlers.ofString()
        );

        if (response.statusCode() != 200 && response.statusCode() != 201) {
            throw new ClaudeApiException(response.statusCode(), response.body());
        }

        Map<String, Object> parsed = objectMapper.readValue(response.body(), Map.class);
        String fileId   = (String) parsed.get("id");
        String mimeType = (String) parsed.get("mime_type");
        String fname    = (String) parsed.get("filename");
        Number size     = (Number) parsed.get("size_bytes");

        if (fileId == null || fileId.isBlank()) {
            throw new ClaudeApiException(response.statusCode(),
                    "Upload response missing 'id': " + response.body());
        }

        log.info("[Claude] File uploaded: id=" + fileId + " mime=" + mimeType);
        return new FileUploadResult(fileId, fname, mimeType, size == null ? 0L : size.longValue());
    }

    /**
     * 将 api-url（如 https://api.anthropic.com/v1/messages）中的路径替换为 /v1/files
     */
    private String resolveFilesEndpoint() {
        String apiUrl = props.getApiUrl();
        try {
            URI uri = URI.create(apiUrl);
            String base = uri.getScheme() + "://" + uri.getAuthority();
            return base + FILES_ENDPOINT_PATH;
        } catch (Exception e) {
            // 兜底：直接在 api-url 所在的前缀下拼接
            int idx = apiUrl.indexOf("/v1/");
            if (idx > 0) {
                return apiUrl.substring(0, idx) + FILES_ENDPOINT_PATH;
            }
            return apiUrl.replaceAll("/+$", "") + FILES_ENDPOINT_PATH;
        }
    }

    /**
     * 手工拼装 multipart/form-data 请求体（Java HttpClient 不原生支持）
     */
    private byte[] buildMultipartBody(String boundary,
                                      String fieldName,
                                      String filename,
                                      String contentType,
                                      byte[] fileBytes) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        String prefix =
                "--" + boundary + "\r\n" +
                        "Content-Disposition: form-data; name=\"" + fieldName + "\"; filename=\"" + escapeQuotes(filename) + "\"\r\n" +
                        "Content-Type: " + contentType + "\r\n\r\n";
        String suffix = "\r\n--" + boundary + "--\r\n";

        out.write(prefix.getBytes(StandardCharsets.UTF_8));
        out.write(fileBytes);
        out.write(suffix.getBytes(StandardCharsets.UTF_8));
        return out.toByteArray();
    }

    private String escapeQuotes(String s) {
        return s == null ? "" : s.replace("\"", "\\\"");
    }

    // ─── 代理构建 ─────────────────────────────────────────────────────────

    private HttpClient buildHttpClient(ClaudeProperties props) {
        HttpClient.Builder builder = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(props.getTimeoutSeconds()));

        ClaudeProperties.Proxy proxyCfg = props.getProxy();

        switch (proxyCfg.getMode()) {
            case NONE -> {
                builder.proxy(ProxySelector.of(null));
                log.info("[Claude] Proxy: NONE (direct connection)");
            }
            case CUSTOM -> {
                if (proxyCfg.getHost() == null || proxyCfg.getHost().isBlank()) {
                    log.warning("[Claude] Proxy mode is CUSTOM but proxy.host is not set, falling back to SYSTEM");
                    builder.proxy(ProxySelector.getDefault());
                } else {
                    Proxy.Type type = "SOCKS5".equalsIgnoreCase(proxyCfg.getType())
                            ? Proxy.Type.SOCKS : Proxy.Type.HTTP;
                    InetSocketAddress addr = new InetSocketAddress(proxyCfg.getHost(), proxyCfg.getPort());
                    ProxySelector selector = ProxySelector.of(addr);

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
                ProxySelector systemProxy = resolveSystemProxy();
                builder.proxy(systemProxy);
                log.info("[Claude] Proxy: SYSTEM → " + describeProxy(systemProxy));
            }
        }

        return builder.build();
    }

    private ProxySelector resolveSystemProxy() {
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

    // ─── 结果与异常 ───────────────────────────────────────────────────────

    /**
     * 文件上传结果
     */
    public static class FileUploadResult {
        private final String fileId;
        private final String filename;
        private final String mimeType;
        private final long   sizeBytes;

        public FileUploadResult(String fileId, String filename, String mimeType, long sizeBytes) {
            this.fileId    = fileId;
            this.filename  = filename;
            this.mimeType  = mimeType;
            this.sizeBytes = sizeBytes;
        }

        public String getFileId()   { return fileId;    }
        public String getFilename() { return filename;  }
        public String getMimeType() { return mimeType;  }
        public long   getSizeBytes(){ return sizeBytes; }
    }

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
