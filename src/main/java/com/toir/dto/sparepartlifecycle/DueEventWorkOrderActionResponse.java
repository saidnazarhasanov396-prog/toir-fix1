package com.toir.dto.sparepartlifecycle;

import com.toir.dto.workorder.WorkOrderDto;
import java.util.UUID;

public record DueEventWorkOrderActionResponse(UUID dueEventId, WorkOrderDto workOrder, boolean replayed) { }
