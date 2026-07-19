package com.toir.dto.analytics;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record DashboardIntegrationResponse(
        String schemaVersion,
        String moduleCode,
        String sourceSystem,
        Instant generatedAt,
        long sourceRevision,
        Period period,
        Scope scope,
        Freshness freshness,
        List<Dataset> datasets
) {
    public static DashboardIntegrationResponse fresh(
            String moduleCode,
            String scopeKey,
            Instant generatedAt,
            List<SourceDataset> sourceDatasets
    ) {
        long revision = Math.max(1L, generatedAt.toEpochMilli());
        UUID organizationId = UUID.nameUUIDFromBytes(
                (moduleCode + ":" + scopeKey).getBytes(StandardCharsets.UTF_8)
        );
        List<Dataset> datasets = sourceDatasets.stream()
                .map(source -> new Dataset(
                        source.type(), "1.0", revision, generatedAt, source.data()
                ))
                .toList();
        return new DashboardIntegrationResponse(
                "1.0", moduleCode, moduleCode, generatedAt, revision,
                new Period(generatedAt.minusSeconds(30L * 86_400L), generatedAt, "Asia/Tashkent"),
                new Scope(organizationId, List.of()),
                new Freshness("FRESH", generatedAt, 300L),
                datasets
        );
    }

    public record Period(Instant from, Instant to, String timeZone) {}
    public record Scope(UUID organizationId, List<UUID> siteIds) {}
    public record Freshness(String status, Instant lastSuccessfulSyncAt, long staleAfterSeconds) {}
    public record Dataset(String type, String schemaVersion, long sourceRevision, Instant generatedAt, Object data) {}
    public record SourceDataset(String type, Object data) {}
}
