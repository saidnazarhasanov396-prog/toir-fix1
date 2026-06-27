package com.toir.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.integrations.faktura-uz")
public class FakturaUzProperties {
    private Endpoints endpoints = new Endpoints();

    @Getter
    @Setter
    public static class Endpoints {
        private String auth = "https://account.faktura.uz/token";
        private String getDocuments = "https://api.faktura.uz/Api/Document/GetDocuments";
        private String getDocumentsContent = "https://api.faktura.uz/Api/Document/GetDocumentsContent";
        private String getUserDetails = "https://api.faktura.uz/Api/account/getuserdetails";
    }
}
