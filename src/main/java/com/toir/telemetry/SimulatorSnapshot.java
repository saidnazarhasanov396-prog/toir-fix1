package com.toir.telemetry;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record SimulatorSnapshot(List<Asset> assets, Instant sentAt) {

    public SimulatorSnapshot {
        assets = List.copyOf(assets);
    }

    public record Asset(String assetId, Map<String, Metric> metrics) {
        public Asset {
            metrics = Map.copyOf(metrics);
        }
    }

    public record Metric(double value, String unit) {
    }
}
