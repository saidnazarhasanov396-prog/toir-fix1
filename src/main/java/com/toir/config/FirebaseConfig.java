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
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StringUtils;

@Slf4j
@Configuration
@EnableConfigurationProperties(FirebaseProperties.class)
public class FirebaseConfig {

    @Bean
    @Conditional(FirebaseCredentialsCondition.class)
    FirebaseApp firebaseApp(FirebaseProperties properties) throws IOException {
        if (!FirebaseApp.getApps().isEmpty()) {
            return FirebaseApp.getApps().getFirst();
        }

        GoogleCredentials credentials = loadCredentials(properties);

        FirebaseOptions.Builder optionsBuilder = FirebaseOptions.builder()
                .setCredentials(credentials);
        if (StringUtils.hasText(properties.getProjectId())) {
            optionsBuilder.setProjectId(properties.getProjectId());
        }
        return FirebaseApp.initializeApp(optionsBuilder.build());
    }

    @Bean
    @Conditional(FirebaseCredentialsCondition.class)
    FirebaseMessaging firebaseMessaging(FirebaseApp firebaseApp) {
        return FirebaseMessaging.getInstance(firebaseApp);
    }

    private GoogleCredentials loadCredentials(FirebaseProperties properties) throws IOException {
        if (StringUtils.hasText(properties.getServiceAccountBase64())) {
            byte[] decoded = Base64.getDecoder().decode(properties.getServiceAccountBase64());
            return GoogleCredentials.fromStream(new ByteArrayInputStream(decoded));
        }
        if (StringUtils.hasText(properties.getServiceAccountJson())) {
            return GoogleCredentials.fromStream(
                    new ByteArrayInputStream(properties.getServiceAccountJson().getBytes(StandardCharsets.UTF_8)));
        }
        if (StringUtils.hasText(properties.getServiceAccountFile())) {
            try (InputStream in = resolveServiceAccountStream(properties.getServiceAccountFile())) {
                return GoogleCredentials.fromStream(in);
            }
        }
        throw new IllegalStateException("No Firebase credential source configured");
    }

    private InputStream resolveServiceAccountStream(String path) throws IOException {
        if (path.startsWith("classpath:")) {
            return new ClassPathResource(path.substring("classpath:".length())).getInputStream();
        }
        return Files.newInputStream(Path.of(path));
    }
}
