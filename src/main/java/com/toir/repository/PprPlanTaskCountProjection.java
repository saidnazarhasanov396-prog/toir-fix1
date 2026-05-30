package com.toir.repository;

import java.util.UUID;

public interface PprPlanTaskCountProjection {
    UUID getPlanId();

    Long getTaskCount();
}
