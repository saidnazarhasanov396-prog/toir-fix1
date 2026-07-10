package com.toir.dto.sparepartlifecycle;

import com.toir.entity.sparepartlifecycle.SparePartInstallation;

public record SparePartLifecycleResult(
        SparePartInstallation installation,
        SparePartInstallation removedInstallation,
        boolean replay
) {
}
