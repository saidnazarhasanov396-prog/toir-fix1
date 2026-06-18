package com.toir.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Configuration
@EnableConfigurationProperties(FirebaseProperties.class)
@Slf4j
public class FirebaseConfig {

    @Bean
    @Conditional(FirebaseCredentialsCondition.class)
    public FirebaseApp firebaseApp(FirebaseProperties properties) throws IOException {
        FirebaseDiagnostics.Status status = FirebaseDiagnostics.fromProperties(properties);
        if (!FirebaseApp.getApps().isEmpty()) {
            log.info("FirebaseApp already exists; reusing default Firebase application");
            return FirebaseApp.getInstance();
        }
        GoogleCredentials credentials;
        try (InputStream inputStream = serviceAccountStream(properties)) {
            credentials = GoogleCredentials.fromStream(inputStream);
        } catch (IOException | RuntimeException ex) {
            log.warn("FirebaseApp initialization failed; credentialSource={}, credentialConfigKey={}, projectIdPresent={}: {}",
                    status.credentialSource(),
                    status.credentialConfigKey(),
                    status.projectIdPresent(),
                    ex.getMessage());
            throw ex;
        }

        FirebaseOptions.Builder builder = FirebaseOptions.builder()
                .setCredentials(credentials);
        if (StringUtils.hasText(properties.getProjectId())) {
            builder.setProjectId(properties.getProjectId());
        }
        FirebaseApp app = FirebaseApp.initializeApp(builder.build());
        log.info("FirebaseApp created for projectIdPresent={}, credentialSource={}",
                status.projectIdPresent(), status.credentialSource());
        return app;
    }

    @Bean
    @Conditional(FirebaseCredentialsCondition.class)
    public FirebaseMessaging firebaseMessaging(FirebaseApp firebaseApp) {
        FirebaseMessaging messaging = FirebaseMessaging.getInstance(firebaseApp);
        log.info("FirebaseMessaging bean created for FirebaseApp {}", firebaseApp.getName());
        return messaging;
    }

    private InputStream serviceAccountStream(FirebaseProperties properties) throws IOException {
        if (StringUtils.hasText(properties.getServiceAccountBase64())) {
            byte[] decoded = Base64.getDecoder().decode(properties.getServiceAccountBase64());
            return new ByteArrayInputStream(decoded);
        }
        if (StringUtils.hasText(properties.getServiceAccountJson())) {
            return new ByteArrayInputStream(properties.getServiceAccountJson().getBytes(StandardCharsets.UTF_8));
        }
        if (StringUtils.hasText(properties.getServiceAccountFile())) {
            return new FileInputStream(properties.getServiceAccountFile());
        }
        throw new IllegalStateException("Firebase service account must be configured when "
                + FirebaseDiagnostics.ENABLED_KEY + "=true");
    }
}
