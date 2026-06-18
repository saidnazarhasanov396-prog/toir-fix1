package com.toir.config;

import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.FirebaseMessaging;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class FirebaseStartupStatusLogger implements ApplicationRunner {

    private final FirebaseProperties properties;
    private final ObjectProvider<FirebaseApp> firebaseAppProvider;
    private final ObjectProvider<FirebaseMessaging> firebaseMessagingProvider;

    @Override
    public void run(ApplicationArguments args) {
        FirebaseDiagnostics.Status status = FirebaseDiagnostics.fromProperties(properties);
        FirebaseApp firebaseApp = firebaseAppProvider.getIfAvailable();
        FirebaseMessaging firebaseMessaging = firebaseMessagingProvider.getIfAvailable();

        log.info("Firebase startup diagnostics: enabled={}, projectIdConfigured={}, credentialSource={}, credentialConfigKey={}, credentialsAvailable={}, firebaseAppInitialized={}, firebaseMessagingBeanCreated={}",
                status.enabled(),
                status.projectIdPresent(),
                status.credentialSource(),
                status.credentialConfigKey(),
                status.credentialsAvailable(),
                firebaseApp != null,
                firebaseMessaging != null);

        if (status.serviceAccountFilePath() != null) {
            log.info("Firebase service account file diagnostics: path={}, exists={}, regularFile={}, readable={}",
                    status.serviceAccountFilePath(),
                    status.serviceAccountFileExists(),
                    status.serviceAccountFileRegularFile(),
                    status.serviceAccountFileReadable());
        }

        if (status.enabled() && !status.firebaseConfigurationReady()) {
            log.warn("Firebase push is enabled but FirebaseMessaging will not be created; missingOrInvalidConfigKeys={}",
                    status.missingOrInvalidConfigKeys());
        }
    }
}
