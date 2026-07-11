package com.toir.exception;

import com.toir.dto.plannedshutdown.PlannedShutdownBlocker;
import org.springframework.http.HttpStatus;

import java.util.List;

public class PlannedShutdownBlockerException extends RestException {
    private final Long version;
    private final List<PlannedShutdownBlocker> blockers;

    public PlannedShutdownBlockerException(HttpStatus status, String message, Long version,
            List<PlannedShutdownBlocker> blockers) {
        super(message, status);
        this.version = version;
        this.blockers = blockers == null ? List.of() : List.copyOf(blockers);
    }

    public Long getVersion() {
        return version;
    }

    public List<PlannedShutdownBlocker> getBlockers() {
        return blockers;
    }
}
