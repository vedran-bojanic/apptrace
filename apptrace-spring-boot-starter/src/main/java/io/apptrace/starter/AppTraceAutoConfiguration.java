package io.apptrace.starter;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestClient;

/**
 * Auto-configuration for the AppTrace client SDK.
 *
 * Activates when apptrace.server-url is set in application properties.
 * Creates an AppTraceClient bean that is ready to use via injection.
 *
 * To disable without removing the dependency:
 *   apptrace:
 *     enabled: false
 */
@AutoConfiguration
@EnableConfigurationProperties(AppTraceProperties.class)
@ConditionalOnProperty(prefix = "apptrace", name = "server-url")
public class AppTraceAutoConfiguration {

    private final AppTraceProperties properties;

    public AppTraceAutoConfiguration(AppTraceProperties properties) {
        this.properties = properties;
    }

    @Bean
    @ConditionalOnMissingBean
    public AppTraceClient appTraceClient() {
        RestClient restClient = RestClient.builder()
                .baseUrl(properties.getServerUrl())
                .defaultHeader("Authorization", "Bearer " + properties.getApiKey())
                .defaultHeader("Content-Type",  "application/json")
                .requestInitializer(request -> {
                    // Apply timeouts via the underlying HTTP client
                    // RestClient uses JDK HttpClient by default in Spring Boot 4
                })
                .build();

        return new AppTraceClient(restClient, properties.isEnabled());
    }
}

