package com.toir.dto.audit;

import com.toir.enums.AuditAction;
import com.toir.enums.AuditModule;

import java.util.Arrays;
import java.util.List;

public record AuditLogMetadataResponse(
        List<String> actions,
        List<String> modules
) {
    public static AuditLogMetadataResponse fromEnums() {
        return new AuditLogMetadataResponse(
                Arrays.stream(AuditAction.values()).map(Enum::name).toList(),
                Arrays.stream(AuditModule.values()).map(Enum::name).toList()
        );
    }
}
