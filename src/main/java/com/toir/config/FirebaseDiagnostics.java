package com.toir.config;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

public final class FirebaseDiagnostics {

    public static final String ENABLED_KEY = "APP_FIREBASE_ENABLED";
    static final String PROJECT_ID_KEY = "APP_FIREBASE_PROJECT_ID";
    static final String SERVICE_ACCOUNT_BASE64_KEY = "APP_FIREBASE_SERVICE_ACCOUNT_BASE64";
    static final String SERVICE_ACCOUNT_JSON_KEY = "APP_FIREBASE_SERVICE_ACCOUNT_JSON";
    static final String SERVICE_ACCOUNT_FILE_KEY = "APP_FIREBASE_SERVICE_ACCOUNT_FILE";
    static final String ANY_CREDENTIAL_KEY = SERVICE_ACCOUNT_BASE64_KEY + " or "
            + SERVICE_ACCOUNT_JSON_KEY + " or "
            + SERVICE_ACCOUNT_FILE_KEY;

    private FirebaseDiagnostics() {
    }

    static Status fromEnvironment(Environment environment) {
        return inspect(
                environment.getProperty("app.firebase.enabled", Boolean.class, false),
                environment.getProperty("app.firebase.project-id"),
                environment.getProperty("app.firebase.service-account-base64"),
                environment.getProperty("app.firebase.service-account-json"),
                environment.getProperty("app.firebase.service-account-file")
        );
    }

    public static Status fromProperties(FirebaseProperties properties) {
        return inspect(
                properties.isEnabled(),
                properties.getProjectId(),
                properties.getServiceAccountBase64(),
                properties.getServiceAccountJson(),
                properties.getServiceAccountFile()
        );
    }

    private static Status inspect(boolean enabled,
                                  String projectId,
                                  String serviceAccountBase64,
                                  String serviceAccountJson,
                                  String serviceAccountFile) {
        boolean projectIdPresent = StringUtils.hasText(projectId);
        Credential credential = credential(serviceAccountBase64, serviceAccountJson, serviceAccountFile);
        List<String> missingOrInvalidConfigKeys = new ArrayList<>();
        if (enabled && !projectIdPresent) {
            missingOrInvalidConfigKeys.add(PROJECT_ID_KEY);
        }
        if (enabled && !credential.available()) {
            missingOrInvalidConfigKeys.add(credential.configKey());
        }
        return new Status(
                enabled,
                projectIdPresent,
                credential.source(),
                credential.configKey(),
                credential.available(),
                credential.filePath(),
                credential.fileExists(),
                credential.fileRegularFile(),
                credential.fileReadable(),
                enabled && projectIdPresent && credential.available(),
                List.copyOf(missingOrInvalidConfigKeys)
        );
    }

    private static Credential credential(String serviceAccountBase64,
                                         String serviceAccountJson,
                                         String serviceAccountFile) {
        if (StringUtils.hasText(serviceAccountBase64)) {
            return Credential.inline("base64", SERVICE_ACCOUNT_BASE64_KEY);
        }
        if (StringUtils.hasText(serviceAccountJson)) {
            return Credential.inline("json", SERVICE_ACCOUNT_JSON_KEY);
        }
        if (!StringUtils.hasText(serviceAccountFile)) {
            return Credential.none();
        }

        try {
            Path path = Path.of(serviceAccountFile);
            boolean exists = Files.exists(path);
            boolean regularFile = Files.isRegularFile(path);
            boolean readable = Files.isReadable(path);
            return Credential.file(serviceAccountFile, exists, regularFile, readable);
        } catch (InvalidPathException ex) {
            return Credential.file(serviceAccountFile, false, false, false);
        }
    }

    private record Credential(
            String source,
            String configKey,
            boolean available,
            String filePath,
            boolean fileExists,
            boolean fileRegularFile,
            boolean fileReadable
    ) {
        static Credential inline(String source, String configKey) {
            return new Credential(source, configKey, true, null, false, false, false);
        }

        static Credential file(String filePath, boolean exists, boolean regularFile, boolean readable) {
            return new Credential(
                    "file",
                    SERVICE_ACCOUNT_FILE_KEY,
                    exists && regularFile && readable,
                    filePath,
                    exists,
                    regularFile,
                    readable
            );
        }

        static Credential none() {
            return new Credential("none", ANY_CREDENTIAL_KEY, false, null, false, false, false);
        }
    }

    public record Status(
            boolean enabled,
            boolean projectIdPresent,
            String credentialSource,
            String credentialConfigKey,
            boolean credentialsAvailable,
            String serviceAccountFilePath,
            boolean serviceAccountFileExists,
            boolean serviceAccountFileRegularFile,
            boolean serviceAccountFileReadable,
            boolean firebaseConfigurationReady,
            List<String> missingOrInvalidConfigKeys
    ) {
    }
}
