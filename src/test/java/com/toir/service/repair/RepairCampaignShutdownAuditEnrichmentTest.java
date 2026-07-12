package com.toir.service.repair;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.toir.audit.AuditDeduplicationRegistry;
import com.toir.audit.AuditRedactionService;
import com.toir.dto.repaircampaign.RepairCampaignShutdownLinkResponse;
import com.toir.enums.AuditAction;
import com.toir.enums.AuditModule;
import com.toir.security.AuthenticatedUser;
import com.toir.security.SecurityScope;
import com.toir.service.AuditLogService;
import com.toir.util.AuditBuilderService;
import com.toir.util.AuditSerializationService;
import com.toir.util.RequestContext;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RepairCampaignShutdownAuditEnrichmentTest {

    @Test
    void relationshipSnapshotsRetainIdentityVersionsActorAndCorrelation() {
        AuditLogService logService = mock(AuditLogService.class);
        SecurityScope securityScope = mock(SecurityScope.class);
        RequestContext requestContext = mock(RequestContext.class);
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        AuditBuilderService builder = new AuditBuilderService(
                new AuditSerializationService(mapper), logService, securityScope, requestContext,
                new AuditDeduplicationRegistry(), new AuditRedactionService(mapper));
        UUID actorId = UUID.randomUUID(); UUID linkId = UUID.randomUUID();
        UUID campaignId = UUID.randomUUID(); UUID shutdownId = UUID.randomUUID();
        when(securityScope.currentUser()).thenReturn(new AuthenticatedUser(actorId.toString(), "reviewer",
                "reviewer@example.com", "Reviewer", null, "TECHNICAL_DIRECTOR", List.of()));
        when(requestContext.getCorrelationId()).thenReturn("task3-correlation");
        when(requestContext.getIpAddress()).thenReturn("127.0.0.1");
        when(requestContext.getUserAgent()).thenReturn("test");
        when(requestContext.getMethod()).thenReturn("DELETE");
        when(requestContext.getPath()).thenReturn("/api/v1/repair-campaigns/links");
        RepairCampaignShutdownLinkResponse before = new RepairCampaignShutdownLinkResponse(
                linkId, campaignId, shutdownId, 2L, 3L, true);
        RepairCampaignShutdownLinkResponse after = new RepairCampaignShutdownLinkResponse(
                linkId, campaignId, shutdownId, 3L, 4L, false);

        builder.log("repair_campaign_shutdown_link", campaignId.toString(), AuditAction.DELETE,
                AuditModule.REPAIR_CAMPAIGN, "unlink", before, after);

        ArgumentCaptor<String> previous = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> current = ArgumentCaptor.forClass(String.class);
        verify(logService).recordDetailed(eq(actorId), eq(AuditModule.REPAIR_CAMPAIGN),
                eq("repair_campaign_shutdown_link"), eq(campaignId.toString()), eq(AuditAction.DELETE),
                eq("unlink"), any(), any(), any(), previous.capture(), current.capture(), eq("unlink"),
                eq("AUDIT_BUILDER_SERVICE"), any(), any(), eq("task3-correlation"));
        assertThat(previous.getValue()).contains(linkId.toString(), campaignId.toString(), shutdownId.toString(),
                "\"repairCampaignVersion\":2", "\"plannedShutdownVersion\":3", "\"active\":true");
        assertThat(current.getValue()).contains(linkId.toString(), campaignId.toString(), shutdownId.toString(),
                "\"repairCampaignVersion\":3", "\"plannedShutdownVersion\":4", "\"active\":false");
    }
}
