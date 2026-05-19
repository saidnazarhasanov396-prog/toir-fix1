package com.toir.security;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Setter
@Getter
@ConfigurationProperties(prefix = "app.security.jwt")
public class JwtProperties {


    private String secret;
    private long expirationMinutes = 720;
    private String issuer = "toir-backend";

}
