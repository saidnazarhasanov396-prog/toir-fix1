package com.toir.dto.sparepartlifecycle;

import org.springframework.data.domain.Page;

public record SparePartLifecycleAggregateResponse(
        SparePartLifecycleSummary summary,
        Page<SparePartLifecycleItem> items
) { }
