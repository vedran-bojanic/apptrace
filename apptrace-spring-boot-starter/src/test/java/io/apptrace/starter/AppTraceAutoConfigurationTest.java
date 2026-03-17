package io.apptrace.starter;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class AppTraceAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(AppTraceAutoConfiguration.class));

    @Test
    void clientBean_createdWhenServerUrlSet() {
        contextRunner
                .withPropertyValues(
                        "apptrace.server-url=http://localhost:8080",
                        "apptrace.api-key=at_test_key")
                .run(context -> {
                    assertThat(context).hasSingleBean(AppTraceClient.class);
                    assertThat(context).hasSingleBean(AppTraceProperties.class);
                });
    }

    @Test
    void clientBean_notCreatedWhenServerUrlMissing() {
        contextRunner
                .run(context ->
                        assertThat(context).doesNotHaveBean(AppTraceClient.class));
    }

    @Test
    void properties_boundCorrectly() {
        contextRunner
                .withPropertyValues(
                        "apptrace.server-url=http://apptrace.company.com",
                        "apptrace.api-key=at_live_xyz",
                        "apptrace.enabled=false")
                .run(context -> {
                    AppTraceProperties props = context.getBean(AppTraceProperties.class);
                    assertThat(props.getServerUrl()).isEqualTo("http://apptrace.company.com");
                    assertThat(props.getApiKey()).isEqualTo("at_live_xyz");
                    assertThat(props.isEnabled()).isFalse();
                });
    }

    @Test
    void customBean_notOverridden() {
        contextRunner
                .withPropertyValues(
                        "apptrace.server-url=http://localhost:8080",
                        "apptrace.api-key=at_test_key")
                .withBean(AppTraceClient.class,
                        () -> new AppTraceClient(null, false))
                .run(context ->
                        assertThat(context).hasSingleBean(AppTraceClient.class));
    }
}

