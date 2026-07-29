package com.toir.config;

import org.springframework.stereotype.Component;

@Component
public class AnnualMaintenanceApprovalFirstFeature {

    private final AnnualMaintenanceApprovalFirstProperties properties;

    public AnnualMaintenanceApprovalFirstFeature(
            AnnualMaintenanceApprovalFirstProperties properties) {
        this.properties = properties;
    }

    public boolean isEnabled() {
        return properties.isEnabled();
    }
}
