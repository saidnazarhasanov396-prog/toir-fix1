package com.toir.ai.repair;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AiRepairGatewayHookTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void completedJobIsReadyAndExtractsResult() {
        ObjectNode result = mapper.createObjectNode();
        result.put("repair_action", "Replace seal");
        ObjectNode payload = mapper.createObjectNode();
        payload.put("status", "COMPLETED");
        payload.set("result", result);

        assertThat(AiRepairGatewayHook.isReadyForRepair(payload)).isTrue();
        assertThat(AiRepairGatewayHook.extractConclusionPayload(payload).path("repair_action").asText())
                .isEqualTo("Replace seal");
    }

    @Test
    void runningJobIsNotReady() {
        ObjectNode payload = mapper.createObjectNode();
        payload.put("status", "RUNNING");
        assertThat(AiRepairGatewayHook.isReadyForRepair(payload)).isFalse();
    }

    @Test
    void enrichAddsToirBlock() {
        AiRepairGatewayHook hook = new AiRepairGatewayHook(null, null, null, mapper);
        ObjectNode payload = mapper.createObjectNode();
        payload.put("ok", true);
        JsonNode enriched = hook.enrich(
                payload,
                AiRepairApplyResult.applied(AiRepairAction.CREATED, java.util.UUID.randomUUID(), "AI-RR-1")
        );
        assertThat(enriched.path("ok").asBoolean()).isTrue();
        assertThat(enriched.path("toir").path("action").asText()).isEqualTo("CREATED");
        assertThat(enriched.path("toir").path("repairRequestNumber").asText()).isEqualTo("AI-RR-1");
    }
}
