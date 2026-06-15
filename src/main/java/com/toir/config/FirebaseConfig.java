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
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Configuration
@EnableConfigurationProperties(FirebaseProperties.class)
public class FirebaseConfig {

    @Bean
    @ConditionalOnProperty(prefix = "app.firebase", name = "enabled", havingValue = "true")
    public FirebaseApp firebaseApp(FirebaseProperties properties) throws IOException {
        if (!FirebaseApp.getApps().isEmpty()) {
            return FirebaseApp.getInstance();
        }
        GoogleCredentials credentials;
        try (InputStream inputStream = serviceAccountStream(properties)) {
            credentials = GoogleCredentials.fromStream(inputStream);
        }

        FirebaseOptions.Builder builder = FirebaseOptions.builder()
                .setCredentials(credentials);
        if (StringUtils.hasText(properties.getProjectId())) {
            builder.setProjectId(properties.getProjectId());
        }
        return FirebaseApp.initializeApp(builder.build());
    }

    @Bean
    @ConditionalOnProperty(prefix = "app.firebase", name = "enabled", havingValue = "true")
    public FirebaseMessaging firebaseMessaging(FirebaseApp firebaseApp) {
        return FirebaseMessaging.getInstance(firebaseApp);
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
        throw new IllegalStateException("Firebase service account must be configured when app.firebase.enabled=true");
    }
}
