# Realtime Meter Telemetry Integration Design

**Date:** 2026-08-03

**Status:** Approved for implementation

## Objective

Connect the external Go equipment telemetry simulator to TOIR so simulated counter values are ingested continuously, persisted as canonical meter readings, and reflected in the meters table in real time. Ingestion must continue when no browser is open.

## Scope

This design covers:

- a long-lived Java backend WebSocket client for the simulator;
- deterministic simulator asset/metric to TOIR equipment/meter resolution;
- canonical persistence through the existing meter lifecycle service;
- authenticated Java-to-browser WebSocket notifications;
- live updates of the existing meters table and meter history;
- reconnect, duplicate suppression, validation, observability, and automated tests.

The Go simulator source is not part of the current workspace. The integration therefore consumes the documented protocol without requiring simulator changes.

## External Simulator Contract

The Java backend connects to the configured simulator endpoint and sends:

```json
{
  "requestId": "toir-meter-telemetry-subscription",
  "action": "assets.subscribe",
  "payload": {}
}
```

It consumes documented snapshot events:

```json
{
  "type": "event",
  "ok": true,
  "data": {
    "event": "assets.snapshot",
    "assets": [
      {
        "assetId": "0301b754-f675-4cf2-96a2-8a84fc11ebd5",
        "assetTypeId": "EXCAVATOR",
        "status": "RUNNING",
        "metrics": {
          "engine_hours": {
            "value": 1240.51,
            "unit": "h"
          }
        },
        "activeFaults": []
      }
    ],
    "sentAt": "2026-08-03T09:00:00Z"
  }
}
```

`assetId` is a string in the simulator contract. For TOIR-managed assets it must contain the canonical TOIR equipment UUID.

## Architecture

```text
Go simulator
  -- assets.snapshot/WebSocket --> Java telemetry bridge
  -- resolve equipment/meter --> MeterService
  -- transaction --> equipment_meters + meter_readings
  -- meter.reading.updated/WebSocket --> React query cache
  --> meters table/history rerender
```

The Java backend is the only component allowed to turn simulator telemetry into canonical TOIR readings. The browser is a consumer of committed updates, not an ingestion worker. Closing the browser therefore has no effect on ingestion.

## Backend Components

### Simulator WebSocket client

A lifecycle-managed client starts only when telemetry integration is enabled. It connects after application startup, subscribes to `assets.snapshot`, and reconnects with bounded exponential backoff after connection loss. Only one active subscription is maintained per application instance.

Configuration:

- `TOIR_TELEMETRY_ENABLED`, default `false`;
- `TOIR_TELEMETRY_WS_URL`, required when enabled;
- `TOIR_TELEMETRY_RECONNECT_INITIAL`, default `1s`;
- `TOIR_TELEMETRY_RECONNECT_MAX`, default `30s`.

Invalid frames are logged and skipped without terminating the connection. Successful connection, disconnect, retry, resolution failures, rejected readings, and accepted readings have structured logs without credentials or full tokens.

### Equipment and meter resolution

Resolution is deterministic:

1. Parse `assetId` as a UUID and resolve an existing, non-deleted TOIR equipment record.
2. Consider only active meters belonging to that equipment.
3. Resolve a metric in this order:
   - metric key is a meter UUID belonging to the equipment;
   - normalized metric key exactly matches normalized meter name;
   - a standard alias maps to a unique `MeterType`:
     - `engine_hours`, `moto_hours`, `motohours` → `ENGINE_HOURS`;
     - `mileage_km`, `odometer_km` → `MILEAGE_KM`;
     - `cycles`, `cycle_count` → `CYCLES`;
     - `tons_produced` → `TONS_PRODUCED`;
     - `kwh_consumed`, `energy_kwh` → `KWH_CONSUMED`.
4. If no unique meter is found, skip the metric and log a resolution warning.

Name normalization trims whitespace, lowercases with `Locale.ROOT`, and treats spaces and hyphens as underscores. Resolution never creates equipment or meters implicitly.

The simulator metric unit must equal the TOIR meter unit after trim and case-insensitive comparison. A mismatched unit is skipped to prevent silent corruption.

### Reading ingestion

Each resolved metric produces an internal reading command containing `meterId`, `value`, `sentAt`, `source=IOT`, and `deviceId=equipment-telemetry-simulator`.

Before invoking `MeterService`:

- non-finite and negative values are rejected;
- an equal value is treated as a duplicate and skipped;
- an older snapshot timestamp is skipped;
- a lower value is passed only when the meter has a positive rollover value and the normal meter lifecycle rules accept it.

The existing transactional `MeterService.addReading` remains the single authority for monotonicity, rollover calculation, current-value mutation, audit, vehicle synchronization, maintenance evaluation, and lifecycle triggers. The bridge does not update repository entities directly.

One invalid metric does not roll back other valid metrics in the same snapshot. Each reading is ingested in its own transaction boundary.

### Browser WebSocket notifications

After a reading transaction commits, the backend broadcasts:

```json
{
  "type": "event",
  "data": {
    "event": "meter.reading.updated",
    "meterId": "f67c1f29-1d51-4c57-b4f7-520209f29a20",
    "equipmentId": "0301b754-f675-4cf2-96a2-8a84fc11ebd5",
    "value": 1240.51,
    "readAt": "2026-08-03T09:00:00Z",
    "source": "IOT"
  }
}
```

The browser WebSocket endpoint requires a valid JWT and `METER_READ`, `SYSTEM_ADMIN`, or `*` authority. Authentication is validated during the handshake. The endpoint sends no events before authentication succeeds and never accepts meter writes from browser clients.

Only non-sensitive meter update fields are broadcast. The server removes dead sessions and isolates send failures so one client cannot disrupt ingestion.

## Frontend Behavior

The meters page opens the authenticated TOIR meter-update WebSocket while mounted. It reconnects with bounded backoff and closes cleanly on unmount or logout.

For each `meter.reading.updated` event, the page updates matching cached meter rows immediately and invalidates affected meter history and aggregate queries. The canonical REST response remains the recovery source after reconnect, pagination changes, filtering, or missed events.

The UI shows live/offline/reconnecting state without blocking normal meter operations. Existing manual reading, editing, filtering, sorting, and pagination behavior remains unchanged.

## Error Handling and Recovery

- Simulator unavailable: keep Java application healthy and retry in the background.
- Malformed JSON or unsupported event: log and skip.
- Unknown equipment or meter: log a structured warning and skip.
- Duplicate/equal value: skip without an error response.
- Stale timestamp: skip and retain the canonical current reading.
- Decreasing value without valid rollover: reject through existing lifecycle validation and continue processing other metrics.
- Database or downstream failure: log the failed meter and allow the next snapshot to retry a greater canonical value.
- Browser disconnected: persistence continues; REST refetch reconciles state after reconnect.

## Security

- Simulator and frontend WebSocket URLs are configuration, not hardcoded LAN addresses.
- Secrets and JWTs are never logged.
- Browser subscriptions require authenticated read permission.
- Browser clients cannot publish readings through the notification socket.
- Production deployment must use `wss://` and restrict allowed origins.
- The simulator should be reachable only from the backend network in production.

## Performance and Data Volume

The documented simulator emits snapshots every two seconds. Only mapped, changed counter values are persisted. Equal values and unrelated gauge metrics are discarded. Each accepted reading intentionally runs existing maintenance and audit behavior so TOIR business state remains consistent.

If production volume later exceeds acceptable limits, sampling or batching may be added behind configuration. It is not part of this implementation because the requested behavior is per-tick real-time counter updates.

## Testing Strategy

Backend tests cover:

- parsing documented WebSocket frames;
- UUID, name, and standard-alias meter resolution;
- unit mismatch and ambiguous mapping rejection;
- duplicate, stale, invalid, monotonic, and rollover cases;
- isolation of failures between metrics;
- subscribe/reconnect lifecycle;
- after-commit notification emission;
- authenticated/unauthorized browser WebSocket handshake behavior.

Frontend tests cover:

- WebSocket URL construction and authentication;
- documented update-event parsing;
- reconnect behavior and cleanup;
- immutable patching of paginated TanStack Query meter caches;
- invalidation of meter history and stats;
- live connection-state presentation.

Verification includes focused backend/frontend tests, backend compile, frontend typecheck/build, lint, and i18n parity checks.

## Acceptance Criteria

1. With telemetry enabled, Java connects and subscribes to the documented simulator WebSocket.
2. A snapshot for a mapped `COUNTER` creates an `IOT` meter reading and updates `equipment_meters.current_value`.
3. Ingestion continues while all browsers are closed.
4. An open meters table displays the committed value without manual refresh.
5. Repeated or stale snapshots do not create incorrect current values.
6. Unmapped, malformed, or unit-mismatched metrics cannot corrupt canonical data.
7. Connection loss does not crash either application and reconnects automatically.
8. Existing manual reading and lifecycle behavior remains passing.

## Out of Scope

- changing the Go simulator implementation;
- automatically creating TOIR equipment or meters;
- independent start/stop simulation sessions;
- persisting gauge/temperature/pressure telemetry as equipment meters;
- replacing the existing lifecycle and maintenance automation rules.
