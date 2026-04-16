package com.toir.defectlist.dto;

import com.toir.defectlist.DefectList;
import com.toir.defectlist.DefectListStatus;

import java.util.List;
import java.util.UUID;

public record DefectListDto(
        UUID id,
        String code,
        String title,
        UUID equipmentId,
        UUID repairRequestId,
        UUID workOrderId,
        UUID createdById,
        UUID approvedById,
        DefectListStatus status,
        double totalLaborHours,
        double totalEstimatedCost,
        String notes,
        List<DefectListLineDto> lines
) {
    public static DefectListDto from(DefectList d) {
        return new DefectListDto(
                d.getId(),
                d.getCode(),
                d.getTitle(),
                d.getEquipmentId(),
                d.getRepairRequestId(),
                d.getWorkOrderId(),
                d.getCreatedById(),
                d.getApprovedById(),
                d.getStatus(),
                d.getTotalLaborHours(),
                d.getTotalEstimatedCost(),
                d.getNotes(),
                d.getLines().stream().map(DefectListLineDto::from).toList()
        );
    }
}
