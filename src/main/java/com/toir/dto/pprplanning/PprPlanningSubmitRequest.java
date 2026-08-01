package com.toir.dto.pprplanning;

import jakarta.validation.constraints.Size;

public record PprPlanningSubmitRequest(
        @Size(max = 2000) String comment) {
}
