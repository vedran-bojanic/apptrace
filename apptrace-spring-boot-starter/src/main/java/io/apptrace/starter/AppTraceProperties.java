package io.apptrace.starter;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Configuration properties for the AppTrace client SDK.
 *
 * Add to your application.yml:
 *
 *   apptrace:
 *     server-url: https://apptrace.yourcompany.com
 *     api-key: at_live_abc123xyz
 *
 * Optional tuning:
 *
 *   apptrace:
 *     server-url: https://apptrace.yourcompany.com
 *     api-key: at_live_abc123xyz
 *     connect-timeout: 5s
 *     read-timeout: 10s
 *     enabled: true
 */
@ConfigurationProperties(prefix = "apptrace")
public class AppTraceProperties {

    /** Base URL of the AppTrace server. Required. */
    private String serverUrl;

    /** Bearer API key for authentication. Required. */
    private String apiKey;

    /** HTTP connection timeout. Default 5 seconds. */
    private Duration connectTimeout = Duration.ofSeconds(5);

    /** HTTP read timeout. Default 10 seconds. */
    private Duration readTimeout = Duration.ofSeconds(10);

    /**
     * Whether the AppTrace client is enabled.
     * Set to false to disable event sending without removing the dependency.
     * Useful in local dev or test environments.
     */
    private boolean enabled = true;

    // -------------------------------------------------------------------------
    // Getters and setters — needed by @ConfigurationProperties binding
    // -------------------------------------------------------------------------

    public String getServerUrl()               { return serverUrl; }
    public void setServerUrl(String v)         { this.serverUrl = v; }

    public String getApiKey()                  { return apiKey; }
    public void setApiKey(String v)            { this.apiKey = v; }

    public Duration getConnectTimeout()        { return connectTimeout; }
    public void setConnectTimeout(Duration v)  { this.connectTimeout = v; }

    public Duration getReadTimeout()           { return readTimeout; }
    public void setReadTimeout(Duration v)     { this.readTimeout = v; }

    public boolean isEnabled()                 { return enabled; }
    public void setEnabled(boolean v)          { this.enabled = v; }
}
