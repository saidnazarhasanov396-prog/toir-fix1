package com.toir.common.bootstrap;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.bootstrap")
public class BootstrapProperties {

    private boolean createDefaultAdmin = true;
    private String adminUsername = "admin";
    private String adminPassword = "P@ssw0rd123";
    private String adminEmail = "admin@toir.local";
    private String adminFullName = "System Administrator";

    public boolean isCreateDefaultAdmin() {
        return createDefaultAdmin;
    }

    public void setCreateDefaultAdmin(boolean createDefaultAdmin) {
        this.createDefaultAdmin = createDefaultAdmin;
    }

    public String getAdminUsername() {
        return adminUsername;
    }

    public void setAdminUsername(String adminUsername) {
        this.adminUsername = adminUsername;
    }

    public String getAdminPassword() {
        return adminPassword;
    }

    public void setAdminPassword(String adminPassword) {
        this.adminPassword = adminPassword;
    }

    public String getAdminEmail() {
        return adminEmail;
    }

    public void setAdminEmail(String adminEmail) {
        this.adminEmail = adminEmail;
    }

    public String getAdminFullName() {
        return adminFullName;
    }

    public void setAdminFullName(String adminFullName) {
        this.adminFullName = adminFullName;
    }
}
