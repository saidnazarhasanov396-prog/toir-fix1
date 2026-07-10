package com.toir.dto.workorder;

import com.toir.dto.sparepartlifecycle.InstallSparePartCommand;
import com.toir.dto.sparepartlifecycle.RemoveSparePartCommand;
import com.toir.dto.sparepartlifecycle.ReplaceSparePartCommand;
import com.toir.enums.sparepartlifecycle.SparePartLifecycleCommandType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record WorkOrderSparePartLifecycleOperation(
        @NotBlank String clientOperationKey,
        @NotNull SparePartLifecycleCommandType operationType,
        @Valid InstallSparePartCommand install,
        @Valid RemoveSparePartCommand remove,
        @Valid ReplaceSparePartCommand replace
) {
}
