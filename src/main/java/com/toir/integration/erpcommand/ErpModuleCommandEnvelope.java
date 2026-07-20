package com.toir.integration.erpcommand;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.UUID;

public record ErpModuleCommandEnvelope(
    UUID id, String source, String target, String type, String subject, Instant time,
    String kind, String schemaVersion, String aggregateType, UUID aggregateId,
    long aggregateVersion, String idempotencyKey, String correlationId,
    String causationId, JsonNode data
) {
  public String commandType() {
    return type != null && type.startsWith("erp.command.") ? type.substring(12) : "";
  }
  public JsonNode payload() { return data == null ? null : data.path("payload"); }
  public Long expectedRevision() {
    return data != null && data.path("expectedRevision").canConvertToLong()
        ? data.path("expectedRevision").longValue() : null;
  }
}
