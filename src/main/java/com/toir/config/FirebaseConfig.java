package com.toir.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Slf4j
@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(FirebaseProperties.class)
public class FirebaseConfig {

    private final FirebaseProperties properties;

    @Bean
    @Conditional(FirebaseCredentialsCondition.class)
    FirebaseApp firebaseApp() {
        if (!FirebaseApp.getApps().isEmpty()) {
            return FirebaseApp.getApps().getFirst();
        }
        try (InputStream credentials = credentials()) {
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(credentials))
                    .setProjectId(properties.getProjectId())
                    .build();
            return FirebaseApp.initializeApp(options);
        } catch (IOException | RuntimeException ex) {
            log.warn("Firebase initialization skipped: {}", ex.getMessage());
            return null;
        }
    }

    @Bean
    @Conditional(FirebaseCredentialsCondition.class)
    FirebaseMessaging firebaseMessaging(FirebaseApp firebaseApp) {
        return firebaseApp == null ? null : FirebaseMessaging.getInstance(firebaseApp);
    }

    private InputStream credentials() throws IOException {
        if (StringUtils.hasText(properties.getServiceAccountBase64())) {
            return new ByteArrayInputStream(Base64.getDecoder().decode(
                    properties.getServiceAccountBase64().trim()));
        }
        if (StringUtils.hasText(properties.getServiceAccountJson())) {
            return new ByteArrayInputStream(
                    properties.getServiceAccountJson().getBytes(StandardCharsets.UTF_8));
        }
        return Files.newInputStream(Path.of(properties.getServiceAccountFile()));
    }
}
