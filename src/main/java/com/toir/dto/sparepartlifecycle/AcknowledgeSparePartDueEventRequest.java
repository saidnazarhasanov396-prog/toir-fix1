package com.toir.dto.sparepartlifecycle;

import java.time.Instant;

public record AcknowledgeSparePartDueEventRequest(Instant acknowledgedAt) {
}
