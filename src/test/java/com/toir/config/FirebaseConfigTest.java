package com.toir.config;

import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.FirebaseMessaging;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class FirebaseConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(FirebaseConfig.class, FirebaseConfigTest.TestPropertiesConfig.class);

    @AfterEach
    void tearDown() {
        FirebaseApp.getApps().forEach(FirebaseApp::delete);
    }

    @Test
    void doesNotCreateFirebaseBeansWhenConfigurationIsIncomplete() {
        contextRunner
                .withPropertyValues("app.firebase.enabled=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(FirebaseApp.class);
                    assertThat(context).doesNotHaveBean(FirebaseMessaging.class);
                });
    }

    @Test
    void doesNotFailStartupWhenFirebaseIsDisabled() {
        contextRunner
                .withPropertyValues("app.firebase.enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(FirebaseApp.class);
                    assertThat(context).doesNotHaveBean(FirebaseMessaging.class);
                });
    }

    @Configuration
    @EnableConfigurationProperties(FirebaseProperties.class)
    static class TestPropertiesConfig {
    }
}
