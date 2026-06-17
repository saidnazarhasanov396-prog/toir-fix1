package com.toir.config;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.StringUtils;

class FirebaseCredentialsCondition implements Condition {

    private static final Logger log = LoggerFactory.getLogger(FirebaseCredentialsCondition.class);

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        boolean enabled = context.getEnvironment().getProperty("app.firebase.enabled", Boolean.class, false);
        if (!enabled) {
            return false;
        }

        if (StringUtils.hasText(context.getEnvironment().getProperty("app.firebase.service-account-base64"))
                || StringUtils.hasText(context.getEnvironment().getProperty("app.firebase.service-account-json"))) {
            return true;
        }

        String serviceAccountFile = context.getEnvironment().getProperty("app.firebase.service-account-file");
        if (!StringUtils.hasText(serviceAccountFile)) {
            log.warn("Firebase push is enabled but service account credentials are not configured; Firebase messaging will be disabled.");
            return false;
        }

        try {
            if (Files.isRegularFile(Path.of(serviceAccountFile))) {
                return true;
            }
        } catch (InvalidPathException ex) {
            log.warn("Firebase push is enabled but service account file path '{}' is invalid; Firebase messaging will be disabled.",
                    serviceAccountFile);
            return false;
        }

        log.warn("Firebase push is enabled but service account file '{}' was not found; Firebase messaging will be disabled.",
                serviceAccountFile);
        return false;
    }
}
