package com.claude.skill.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Claude Starter 全局配置
 * 集成方在 application.yml 中通过 claude.* 修改所有配置
 */
@ConfigurationProperties(prefix = "claude")
public class ClaudeProperties {

    // ─── API 配置 ────────────────────────────────────────────────
    private String apiKey;
    private String apiUrl = "https://api.anthropic.com/v1/messages";
    private String model = "claude-sonnet-4-20250514";
    private String anthropicVersion = "2023-06-01";
    private int maxTokens = 4096;
    private int timeoutSeconds = 120;

    // ─── 代理配置 ────────────────────────────────────────────────
    private Proxy proxy = new Proxy();

    // ─── Skill 配置 ──────────────────────────────────────────────
    private Skill skill = new Skill();

    // ─── Session 配置 ────────────────────────────────────────────
    private Session session = new Session();

    // ─── Web 端点配置 ─────────────────────────────────────────────
    private Web web = new Web();

    // ===== 代理配置 =====
    public static class Proxy {
        /**
         * 代理模式:
         *   SYSTEM  - 使用系统代理（默认，读取 http.proxyHost / HTTPS_PROXY 等环境变量）
         *   CUSTOM  - 使用下方自定义 host/port
         *   NONE    - 强制直连，忽略系统代理
         */
        private ProxyMode mode = ProxyMode.SYSTEM;
        private String host;
        private int port = 7890;
        private String username;
        private String password;
        private String type = "HTTP"; // HTTP / SOCKS5

        public enum ProxyMode { SYSTEM, CUSTOM, NONE }

        public ProxyMode getMode() { return mode; }
        public void setMode(ProxyMode mode) { this.mode = mode; }
        public String getHost() { return host; }
        public void setHost(String host) { this.host = host; }
        public int getPort() { return port; }
        public void setPort(int port) { this.port = port; }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
    }

    // ===== Skill 配置 =====
    public static class Skill {
        /** .skill 文件存放目录 */
        private String dir = "skills";
        /** 启动时是否自动扫描加载 */
        private boolean autoLoad = true;
        /** 是否监听目录变化热加载（需要集成方自行启用） */
        private boolean watchDir = false;

        public String getDir() { return dir; }
        public void setDir(String dir) { this.dir = dir; }
        public boolean isAutoLoad() { return autoLoad; }
        public void setAutoLoad(boolean autoLoad) { this.autoLoad = autoLoad; }
        public boolean isWatchDir() { return watchDir; }
        public void setWatchDir(boolean watchDir) { this.watchDir = watchDir; }
    }

    // ===== Session 配置 =====
    public static class Session {
        /**
         * 存储类型:
         *   MEMORY  - 内存（默认，重启丢失）
         *   CUSTOM  - 由集成方注入自定义 SessionStore Bean
         */
        private SessionType type = SessionType.MEMORY;
        /** 最大会话数（MEMORY模式下LRU淘汰） */
        private int maxSize = 1000;
        /** 会话过期时间（分钟），0=不过期 */
        private int ttlMinutes = 60;
        /** 每个会话最多保留的消息轮数 */
        private int maxTurnsPerSession = 50;

        public enum SessionType { MEMORY, CUSTOM }

        public SessionType getType() { return type; }
        public void setType(SessionType type) { this.type = type; }
        public int getMaxSize() { return maxSize; }
        public void setMaxSize(int maxSize) { this.maxSize = maxSize; }
        public int getTtlMinutes() { return ttlMinutes; }
        public void setTtlMinutes(int ttlMinutes) { this.ttlMinutes = ttlMinutes; }
        public int getMaxTurnsPerSession() { return maxTurnsPerSession; }
        public void setMaxTurnsPerSession(int maxTurnsPerSession) { this.maxTurnsPerSession = maxTurnsPerSession; }
    }

    // ===== Web 端点配置 =====
    public static class Web {
        /** 是否启用内置 REST 端点 */
        private boolean enabled = true;
        /** API 路径前缀 */
        private String basePath = "/claude";
        private Springdoc springdoc = new Springdoc();

        public static class Springdoc {
            private boolean enabled = true;
            private String group = "claude-skill";
            private String packageToScan = "com.claude.skill.web";

            public boolean isEnabled() { return enabled; }
            public void setEnabled(boolean enabled) { this.enabled = enabled; }
            public String getGroup() { return group; }
            public void setGroup(String group) { this.group = group; }
            public String getPackageToScan() { return packageToScan; }
            public void setPackageToScan(String packageToScan) { this.packageToScan = packageToScan; }
        }

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getBasePath() { return basePath; }
        public void setBasePath(String basePath) { this.basePath = basePath; }
        public Springdoc getSpringdoc() { return springdoc; }
        public void setSpringdoc(Springdoc springdoc) { this.springdoc = springdoc; }
    }

    // ===== Getters/Setters =====
    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    public String getApiUrl() { return apiUrl; }
    public void setApiUrl(String apiUrl) { this.apiUrl = apiUrl; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public String getAnthropicVersion() { return anthropicVersion; }
    public void setAnthropicVersion(String anthropicVersion) { this.anthropicVersion = anthropicVersion; }
    public int getMaxTokens() { return maxTokens; }
    public void setMaxTokens(int maxTokens) { this.maxTokens = maxTokens; }
    public int getTimeoutSeconds() { return timeoutSeconds; }
    public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
    public Proxy getProxy() { return proxy; }
    public void setProxy(Proxy proxy) { this.proxy = proxy; }
    public Skill getSkill() { return skill; }
    public void setSkill(Skill skill) { this.skill = skill; }
    public Session getSession() { return session; }
    public void setSession(Session session) { this.session = session; }
    public Web getWeb() { return web; }
    public void setWeb(Web web) { this.web = web; }
}
