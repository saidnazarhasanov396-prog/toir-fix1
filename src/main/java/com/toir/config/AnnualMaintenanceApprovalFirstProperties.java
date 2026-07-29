package com.toir.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "toir.features.annual-maintenance-approval-first")
public class AnnualMaintenanceApprovalFirstProperties {

    private boolean enabled = false;
}
