# Realtime Meter Telemetry: deployment and verification

## Purpose

This runbook connects the Go telemetry simulator to TOIR.  The backend, not the
browser, consumes simulator snapshots and stores accepted values as canonical
meter readings.  Therefore ingestion continues when no user has `/meters`
open.

## Deployment configuration

Telemetry is disabled unless explicitly enabled. Configure the backend process
with these environment variables:

```dotenv
TOIR_TELEMETRY_ENABLED=true
TOIR_TELEMETRY_WS_URL=ws://<simulator-host>:8080/api/v1/ws
TOIR_TELEMETRY_RECONNECT_INITIAL=PT1S
TOIR_TELEMETRY_RECONNECT_MAX=PT30S
```

`TOIR_TELEMETRY_WS_URL` must be an absolute `ws://` or `wss://` URL. Both
durations must be positive ISO-8601 durations, and the maximum must not be
smaller than the initial delay. In production use `wss://` and restrict the
simulator to the backend network.

After startup the backend sends this one subscription request for each active
simulator connection:

```json
{"requestId":"toir-meter-telemetry-subscription","action":"assets.subscribe","payload":{}}
```

It reconnects in the background after a loss of connection. A simulator outage
does not prevent the TOIR application from starting.

## Required TOIR and simulator test data

Create the TOIR equipment and its active counter meter before enabling the
integration. The simulator must emit an `assets.snapshot` event where:

- `assetId` is the existing, non-deleted TOIR equipment UUID;
- the metric key is either that meter's UUID, its normalized name, or one of
  the supported aliases below;
- `unit` equals the TOIR meter unit after trimming and case-insensitive
  comparison;
- `value` is finite, non-negative, and greater than the meter's current value;
- `sentAt` is not older than the meter's `last_read_at`.

Supported aliases are:

| Metric key | TOIR meter type |
| --- | --- |
| `engine_hours`, `moto_hours`, `motohours` | `ENGINE_HOURS` |
| `mileage_km`, `odometer_km` | `MILEAGE_KM` |
| `cycles`, `cycle_count` | `CYCLES` |
| `tons_produced` | `TONS_PRODUCED` |
| `kwh_consumed`, `energy_kwh` | `KWH_CONSUMED` |

For a name key, normalization lowercases the name, trims it, and converts
spaces and hyphens to underscores. If more than one active meter matches, TOIR
skips the metric rather than choosing one arbitrarily.

Example snapshot:

```json
{
  "type": "event",
  "ok": true,
  "data": {
    "event": "assets.snapshot",
    "assets": [
      {
        "assetId": "0301b754-f675-4cf2-96a2-8a84fc11ebd5",
        "metrics": {
          "engine_hours": { "value": 1240.51, "unit": "h" }
        }
      }
    ],
    "sentAt": "2026-08-03T09:00:00Z"
  }
}
```

TOIR skips equal values and stale snapshots. A decreasing value is evaluated
by the existing meter lifecycle rules and is only accepted for a valid rollover.
Invalid, unmapped, ambiguous, and unit-mismatched metrics are not persisted.

## Validate the end-to-end flow

1. Record the target meter's current value and timestamp, then start the
   simulator with a higher value using the exact matching unit.
2. Confirm a new reading and current value in the database:

   ```sql
   SELECT id, current_value, last_read_at
   FROM equipment_meters
   WHERE id = '<meter-uuid>';

   SELECT meter_id, value, read_at, source, device_id
   FROM meter_readings
   WHERE meter_id = '<meter-uuid>'
   ORDER BY read_at DESC
   LIMIT 1;
   ```

   The second query must show `source = 'IOT'` and
   `device_id = 'equipment-telemetry-simulator'`.
3. Request `GET /api/v1/meters/<meter-uuid>` as a user with `METER_READ`,
   `SYSTEM_ADMIN`, or `*`; confirm its `currentValue` and `lastReadAt` match
   the accepted reading. Then open `/meters`, send another increasing snapshot,
   and confirm that the table's value and timestamp change without a manual
   refresh. The page's realtime badge should show a live connection.
4. Close all browser tabs, send one more increasing snapshot, and repeat the
   database check. This confirms ingestion is independent of browser sessions.

The browser receives only committed events in this form:

```json
{
  "type": "event",
  "data": {
    "event": "meter.reading.updated",
    "meterId": "<meter-uuid>",
    "equipmentId": "<equipment-uuid>",
    "value": 1240.51,
    "readAt": "2026-08-03T09:00:00Z",
    "source": "IOT"
  }
}
```

## Browser WebSocket security

The read-only notification endpoint is `GET /api/v1/ws/meters`. Browsers pass
the JWT as `?access_token=<JWT>` because the native browser WebSocket API cannot
set an `Authorization` header. The query value is the raw JWT without a `Bearer `
prefix. The backend accepts it only for `GET` requests to that exact path, even
when a reverse proxy does not expose the `Upgrade` header to the authentication
filter; an `Authorization: Bearer <JWT>` header still has precedence.

This is a native WebSocket endpoint, not STOMP or SockJS. The reverse proxy must
still forward `Upgrade` and `Connection` correctly for the server to return a
successful `101 Switching Protocols` response.

Query tokens can still be exposed by reverse-proxy request logging, browser
history tooling, or telemetry that records full URLs. Configure proxies and
observability systems to redact `access_token`, avoid logging full WebSocket
URLs, use HTTPS/WSS in production, and keep the endpoint's allowed origins
restricted. Browser clients cannot write meter readings through this socket.

## Troubleshooting

| Symptom | Check and corrective action |
| --- | --- |
| No simulator connection | Set `TOIR_TELEMETRY_ENABLED=true`; validate the absolute WebSocket URL, DNS/network route, and simulator endpoint. The backend will retry with bounded backoff. |
| Connection opens but no readings appear | Confirm the simulator sends `type=event`, `ok=true`, `data.event=assets.snapshot`, an ISO `sentAt`, and a textual `assetId`/metric unit. |
| Snapshot is ignored | Check that the equipment exists and is not deleted, the meter is active, key resolution is unique, and unit matches exactly (ignoring case/outer whitespace). |
| Reading is not created | Use a finite non-negative value greater than the current value and a non-stale `sentAt`; inspect existing rollover configuration for lower counter values. |
| Database updates but `/meters` does not | Confirm the user has `METER_READ`, `SYSTEM_ADMIN`, or `*`; check the browser WebSocket handshake, allowed origins, and proxy redaction/forwarding configuration. A page reconnect will reconcile from the regular REST data. |
| UI shows reconnecting/offline | Inspect the browser's `/api/v1/ws/meters` WebSocket handshake and token validity. A missing or invalid query JWT is rejected before upgrade, which browsers commonly surface as close code `1006`; the client retries with bounded backoff. Only a post-upgrade policy/authentication close (`1008`, `4401`, or `4403`) stops retries until a valid authenticated session is available. |
