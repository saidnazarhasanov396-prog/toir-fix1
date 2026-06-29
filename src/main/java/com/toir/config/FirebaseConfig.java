package com.toir.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Configuration
@Conditional(FirebaseCredentialsCondition.class)
public class FirebaseConfig {

    @Bean
    FirebaseApp firebaseApp(FirebaseProperties properties) {
        if (!FirebaseApp.getApps().isEmpty()) {
            return FirebaseApp.getApps().getFirst();
        }

        try {
            FirebaseOptions.Builder builder = FirebaseOptions.builder()
                    .setCredentials(credentials(properties));
            if (StringUtils.hasText(properties.getProjectId())) {
                builder.setProjectId(properties.getProjectId());
            }
            FirebaseOptions options = builder.build();
            return FirebaseApp.initializeApp(options);
        } catch (IOException ex) {
            throw new IllegalStateException("Firebase initialization failed", ex);
        }
    }

    @Bean
    FirebaseMessaging firebaseMessaging(FirebaseApp firebaseApp) {
        return FirebaseMessaging.getInstance(firebaseApp);
    }

    private GoogleCredentials credentials(FirebaseProperties properties) throws IOException {
        if (StringUtils.hasText(properties.getServiceAccountBase64())) {
            byte[] decoded = Base64.getDecoder().decode(properties.getServiceAccountBase64());
            return GoogleCredentials.fromStream(new ByteArrayInputStream(decoded));
        }
        if (StringUtils.hasText(properties.getServiceAccountJson())) {
            byte[] json = properties.getServiceAccountJson().getBytes(StandardCharsets.UTF_8);
            return GoogleCredentials.fromStream(new ByteArrayInputStream(json));
        }
        if (StringUtils.hasText(properties.getServiceAccountFile())) {
            try (var input = Files.newInputStream(Path.of(properties.getServiceAccountFile()))) {
                return GoogleCredentials.fromStream(input);
            }
        }
        throw new IllegalStateException("Firebase credentials are not configured");
    }
}
