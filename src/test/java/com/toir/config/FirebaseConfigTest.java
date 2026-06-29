package com.toir.config;

import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.FirebaseMessaging;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class FirebaseConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(FirebaseConfig.class);

    @Test
    void doesNotInitializeFirebaseMessagingWithoutEnabledCredentials() {
        contextRunner
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(FirebaseApp.class);
                    assertThat(context).doesNotHaveBean(FirebaseMessaging.class);
                });
    }
}
