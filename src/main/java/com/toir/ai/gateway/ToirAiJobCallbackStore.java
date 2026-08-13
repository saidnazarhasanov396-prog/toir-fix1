package com.toir.ai.gateway;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ToirAiJobCallbackStore {

    private final ConcurrentHashMap<UUID, JsonNode> payloads = new ConcurrentHashMap<>();

    public void put(UUID jobId, JsonNode payload) {
        if (jobId == null || payload == null) {
            return;
        }
        payloads.put(jobId, payload);
    }

    public Optional<JsonNode> get(UUID jobId) {
        if (jobId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(payloads.get(jobId));
    }

    public JsonNode remove(UUID jobId) {
        if (jobId == null) {
            return null;
        }
        return payloads.remove(jobId);
    }
}
