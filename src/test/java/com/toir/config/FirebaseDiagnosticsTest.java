package com.toir.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

class FirebaseDiagnosticsTest {

    @Test
    void reportsMissingProjectAndCredentialKeysWhenFirebaseIsEnabledWithoutCredentials() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("app.firebase.enabled", "true");

        FirebaseDiagnostics.Status status = FirebaseDiagnostics.fromEnvironment(environment);

        assertThat(status.enabled()).isTrue();
        assertThat(status.projectIdPresent()).isFalse();
        assertThat(status.credentialSource()).isEqualTo("none");
        assertThat(status.credentialsAvailable()).isFalse();
        assertThat(status.firebaseConfigurationReady()).isFalse();
        assertThat(status.missingOrInvalidConfigKeys())
                .containsExactly(
                        "APP_FIREBASE_PROJECT_ID",
                        "APP_FIREBASE_SERVICE_ACCOUNT_BASE64 or APP_FIREBASE_SERVICE_ACCOUNT_JSON or APP_FIREBASE_SERVICE_ACCOUNT_FILE"
                );
    }

    @Test
    void reportsUnreadableFileCredentialSource() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("app.firebase.enabled", "true")
                .withProperty("app.firebase.project-id", "toir-51480")
                .withProperty("app.firebase.service-account-file", "target/missing-firebase-service-account.json");

        FirebaseDiagnostics.Status status = FirebaseDiagnostics.fromEnvironment(environment);

        assertThat(status.credentialSource()).isEqualTo("file");
        assertThat(status.credentialConfigKey()).isEqualTo("APP_FIREBASE_SERVICE_ACCOUNT_FILE");
        assertThat(status.serviceAccountFileExists()).isFalse();
        assertThat(status.serviceAccountFileReadable()).isFalse();
        assertThat(status.credentialsAvailable()).isFalse();
        assertThat(status.missingOrInvalidConfigKeys())
                .containsExactly("APP_FIREBASE_SERVICE_ACCOUNT_FILE");
    }
}
