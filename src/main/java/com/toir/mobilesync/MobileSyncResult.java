package com.toir.mobilesync;

import java.util.List;

public record MobileSyncResult(
        int readingsAccepted,
        int resultsAccepted,
        int roundsCompleted,
        List<String> errors
) {}
