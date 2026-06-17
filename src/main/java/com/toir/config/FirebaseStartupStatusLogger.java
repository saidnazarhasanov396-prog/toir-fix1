package com.toir.config;

import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.FirebaseMessaging;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
@Slf4j
public class FirebaseStartupStatusLogger implements ApplicationRunner {

    private final FirebaseProperties properties;
    private final ObjectProvider<FirebaseApp> firebaseAppProvider;
    private final ObjectProvider<FirebaseMessaging> firebaseMessagingProvider;

    @Override
    public void run(ApplicationArguments args) {
        boolean projectIdPresent = StringUtils.hasText(properties.getProjectId());
        ServiceAccountStatus serviceAccount = serviceAccountStatus();
        FirebaseApp firebaseApp = firebaseAppProvider.getIfAvailable();
        FirebaseMessaging firebaseMessaging = firebaseMessagingProvider.getIfAvailable();

        log.info("Firebase startup status: enabled={}, projectIdPresent={}, serviceAccountSource={}, serviceAccountAvailable={}, firebaseAppCreated={}, firebaseMessagingBeanCreated={}",
                properties.isEnabled(),
                projectIdPresent,
                serviceAccount.source(),
                serviceAccount.available(),
                firebaseApp != null,
                firebaseMessaging != null);

        if (StringUtils.hasText(properties.getServiceAccountFile())) {
            log.info("Firebase service account file status: path={}, exists={}, regularFile={}, readable={}",
                    properties.getServiceAccountFile(),
                    serviceAccount.fileExists(),
                    serviceAccount.regularFile(),
                    serviceAccount.readable());
        }
    }

    private ServiceAccountStatus serviceAccountStatus() {
        if (StringUtils.hasText(properties.getServiceAccountBase64())) {
            return ServiceAccountStatus.inline("base64");
        }
        if (StringUtils.hasText(properties.getServiceAccountJson())) {
            return ServiceAccountStatus.inline("json");
        }
        if (!StringUtils.hasText(properties.getServiceAccountFile())) {
            return ServiceAccountStatus.none();
        }

        try {
            Path path = Path.of(properties.getServiceAccountFile());
            boolean exists = Files.exists(path);
            boolean regularFile = Files.isRegularFile(path);
            boolean readable = Files.isReadable(path);
            return new ServiceAccountStatus("file", regularFile && readable, exists, regularFile, readable);
        } catch (InvalidPathException ex) {
            log.warn("Firebase service account file path '{}' is invalid: {}",
                    properties.getServiceAccountFile(), ex.getMessage());
            return new ServiceAccountStatus("file", false, false, false, false);
        }
    }

    private record ServiceAccountStatus(
            String source,
            boolean available,
            boolean fileExists,
            boolean regularFile,
            boolean readable
    ) {
        static ServiceAccountStatus inline(String source) {
            return new ServiceAccountStatus(source, true, false, false, false);
        }

        static ServiceAccountStatus none() {
            return new ServiceAccountStatus("none", false, false, false, false);
        }
    }
}
