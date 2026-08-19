package com.toir.ai.repair;

import com.toir.enums.CriticalityLevel;
import com.toir.enums.PriorityLevel;

import java.util.List;

public record AiRepairConclusion(
        boolean broken,
        boolean insufficientMedia,
        String problemKey,
        String title,
        String description,
        PriorityLevel priority,
        CriticalityLevel criticality,
        List<AiRepairDefect> defects
) {
    public AiRepairConclusion {
        defects = defects == null ? List.of() : List.copyOf(defects);
    }
}
