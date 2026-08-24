package com.toir.telemetry;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class SimulatorSnapshotParser {

    private final ObjectMapper objectMapper;

    public SimulatorSnapshotParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Optional<SimulatorSnapshot> parse(String frame) {
        try {
            JsonNode root = objectMapper.readTree(frame);
            if (!isExpectedEnvelope(root)) {
                return Optional.empty();
            }
            return snapshotFrom(root.path("data"));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    public Optional<SimulatorSnapshot> parsePush(String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            if (root == null || !root.isObject()) {
                return Optional.empty();
            }
            return snapshotFrom(root);
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    private Optional<SimulatorSnapshot> snapshotFrom(JsonNode node) {
        Instant sentAt = parseSentAt(node.path("sentAt")).orElse(null);
        JsonNode assets = node.path("assets");
        if (sentAt == null || !assets.isArray()) {
            return Optional.empty();
        }
        List<SimulatorSnapshot.Asset> parsedAssets = new ArrayList<>();
        for (JsonNode asset : assets) {
            parseAsset(asset).ifPresent(parsedAssets::add);
        }
        return Optional.of(new SimulatorSnapshot(parsedAssets, sentAt));
    }

    private boolean isExpectedEnvelope(JsonNode root) {
        return root != null
                && root.isObject()
                && root.path("type").isTextual()
                && "event".equals(root.path("type").textValue())
                && root.path("ok").isBoolean()
                && root.path("ok").booleanValue()
                && root.path("data").isObject()
                && root.path("data").path("event").isTextual()
                && "assets.snapshot".equals(root.path("data").path("event").textValue());
    }

    private Optional<Instant> parseSentAt(JsonNode sentAt) {
        if (!sentAt.isTextual()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Instant.parse(sentAt.textValue()));
        } catch (DateTimeParseException exception) {
            return Optional.empty();
        }
    }

    private Optional<SimulatorSnapshot.Asset> parseAsset(JsonNode asset) {
        if (!asset.isObject() || !asset.path("assetId").isTextual() || !asset.path("metrics").isObject()) {
            return Optional.empty();
        }
        Map<String, SimulatorSnapshot.Metric> metrics = new LinkedHashMap<>();
        asset.path("metrics").fields().forEachRemaining(entry -> parseMetric(entry.getValue())
                .ifPresent(metric -> metrics.put(entry.getKey(), metric)));
        return Optional.of(new SimulatorSnapshot.Asset(asset.path("assetId").textValue(), metrics));
    }

    private Optional<SimulatorSnapshot.Metric> parseMetric(JsonNode metric) {
        if (!metric.isObject() || !metric.path("value").isNumber() || !metric.path("unit").isTextual()) {
            return Optional.empty();
        }
        double value = metric.path("value").doubleValue();
        if (!Double.isFinite(value)) {
            return Optional.empty();
        }
        return Optional.of(new SimulatorSnapshot.Metric(value, metric.path("unit").textValue()));
    }
}
