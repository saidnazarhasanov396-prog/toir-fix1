package com.toir.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.firebase")
public class FirebaseProperties {
    private boolean enabled;
    private String projectId;
    private String serviceAccountFile;
    private String serviceAccountJson;
    private String serviceAccountBase64;
}
