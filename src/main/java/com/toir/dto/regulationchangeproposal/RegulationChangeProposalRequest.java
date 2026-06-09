package com.toir.dto.regulationchangeproposal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record RegulationChangeProposalRequest(
        @NotNull UUID regulationId,
        UUID rcmSnapshotId,
        @NotBlank String title,
        String description,
        Integer proposedPeriodicityValue,
        String proposedPeriodicityUnit,
        UUID proposedTemplateId,
        String changeReason
) {}