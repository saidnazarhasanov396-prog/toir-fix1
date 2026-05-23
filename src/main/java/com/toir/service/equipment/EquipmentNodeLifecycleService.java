package com.toir.service.equipment;

import com.toir.dto.equipmentnode.EquipmentNodeLifecycleDto;
import com.toir.dto.technicaldocument.TechnicalDocumentDto;
import com.toir.entity.FileAsset;
import com.toir.entity.TechnicalDocument;
import com.toir.entity.defects.Defect;
import com.toir.entity.equipment.EquipmentNode;
import com.toir.entity.maintenance.WorkOrder;
import com.toir.exception.RestException;
import com.toir.repository.FileAssetRepository;
import com.toir.repository.TechnicalDocumentRepository;
import com.toir.repository.WorkOrderRepository;
import com.toir.repository.defects.DefectRepository;
import com.toir.repository.equipment.EquipmentNodeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EquipmentNodeLifecycleService {

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 200;

    private final EquipmentNodeRepository equipmentNodeRepository;
    private final DefectRepository defectRepository;
    private final WorkOrderRepository workOrderRepository;
    private final TechnicalDocumentRepository technicalDocumentRepository;
    private final FileAssetRepository fileAssetRepository;

    @Transactional(readOnly = true)
    public EquipmentNodeLifecycleDto getLifecycle(UUID nodeId, boolean includeTimeline, Integer limit) {
        EquipmentNode node = equipmentNodeRepository.findByIdAndIsDeletedFalse(nodeId)
                .orElseThrow(() -> RestException.notFound("Equipment node not found: " + nodeId));
        int resolvedLimit = resolveLimit(limit);

        List<Defect> defects = active(defectRepository.findAllByEquipmentNodeIdAndIsDeletedFalseOrderByUpdatedAtDesc(nodeId));
        List<WorkOrder> workOrders = active(workOrderRepository.findAllByEquipmentNodeIdAndIsDeletedFalseOrderByUpdatedAtDesc(nodeId));
        List<TechnicalDocument> documents = active(technicalDocumentRepository.findAllByEquipmentNodeIdAndIsDeletedFalse(nodeId));
        Map<UUID, TechnicalDocumentDto.FileRef> fileRefsById = buildFileRefs(documents);

        List<EquipmentNodeLifecycleDto.DefectItem> defectItems = defects.stream()
                .limit(resolvedLimit)
                .map(this::toDefectItem)
                .toList();
        List<EquipmentNodeLifecycleDto.WorkOrderItem> workOrderItems = workOrders.stream()
                .limit(resolvedLimit)
                .map(this::toWorkOrderItem)
                .toList();
        List<EquipmentNodeLifecycleDto.DocumentItem> documentItems = documents.stream()
                .limit(resolvedLimit)
                .map(document -> toDocumentItem(document, fileRefFor(document, fileRefsById)))
                .toList();

        return new EquipmentNodeLifecycleDto(
                toNodeSummary(node),
                new EquipmentNodeLifecycleDto.Counts(defects.size(), workOrders.size(), documents.size()),
                defectItems,
                workOrderItems,
                documentItems,
                includeTimeline ? buildTimeline(defects, workOrders, documents, resolvedLimit) : List.of()
        );
    }

    private int resolveLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_LIMIT;
        }
        if (limit < 1) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private <T extends com.toir.entity.BaseEntity> List<T> active(List<T> records) {
        if (records == null || records.isEmpty()) {
            return List.of();
        }
        return records.stream()
                .filter(record -> record != null && !record.isDeleted())
                .toList();
    }

    private EquipmentNodeLifecycleDto.NodeSummary toNodeSummary(EquipmentNode node) {
        return new EquipmentNodeLifecycleDto.NodeSummary(
                node.getId(),
                node.getEquipmentId(),
                node.getParentId(),
                node.getCode(),
                node.getName(),
                node.getNodeType(),
                node.getSerialNumber()
        );
    }

    private EquipmentNodeLifecycleDto.DefectItem toDefectItem(Defect defect) {
        return new EquipmentNodeLifecycleDto.DefectItem(
                defect.getId(),
                defect.getCode(),
                defect.getTitle(),
                defect.getStatus() == null ? null : defect.getStatus().name(),
                defect.getSeverity(),
                defect.getCreatedAt(),
                defect.getUpdatedAt()
        );
    }

    private EquipmentNodeLifecycleDto.WorkOrderItem toWorkOrderItem(WorkOrder workOrder) {
        return new EquipmentNodeLifecycleDto.WorkOrderItem(
                workOrder.getId(),
                workOrder.getNumber(),
                workOrder.getTitle(),
                workOrder.getStatus() == null ? null : workOrder.getStatus().name(),
                workOrder.getType() == null ? null : workOrder.getType().name(),
                workOrder.getWorkType() == null ? null : workOrder.getWorkType().name(),
                workOrder.getPriority() == null ? null : workOrder.getPriority().name(),
                workOrder.getCreatedAt(),
                workOrder.getUpdatedAt()
        );
    }

    private EquipmentNodeLifecycleDto.DocumentItem toDocumentItem(TechnicalDocument document,
                                                                  TechnicalDocumentDto.FileRef file) {
        return new EquipmentNodeLifecycleDto.DocumentItem(
                document.getId(),
                document.getTitle(),
                document.getType() == null ? null : document.getType().name(),
                document.getRevision(),
                document.getDocumentDate(),
                file,
                document.getCreatedAt(),
                document.getUpdatedAt()
        );
    }

    private List<EquipmentNodeLifecycleDto.TimelineItem> buildTimeline(List<Defect> defects,
                                                                       List<WorkOrder> workOrders,
                                                                       List<TechnicalDocument> documents,
                                                                       int limit) {
        List<EquipmentNodeLifecycleDto.TimelineItem> timeline = new ArrayList<>();
        defects.forEach(defect -> timeline.add(new EquipmentNodeLifecycleDto.TimelineItem(
                "DEFECT",
                defect.getId(),
                defect.getTitle(),
                defect.getStatus() == null ? null : defect.getStatus().name(),
                defect.getSeverity(),
                occurredAt(defect),
                defect.getCreatedAt()
        )));
        workOrders.forEach(workOrder -> timeline.add(new EquipmentNodeLifecycleDto.TimelineItem(
                "WORK_ORDER",
                workOrder.getId(),
                workOrder.getTitle(),
                workOrder.getStatus() == null ? null : workOrder.getStatus().name(),
                workOrder.getType() == null ? null : workOrder.getType().name(),
                occurredAt(workOrder),
                workOrder.getCreatedAt()
        )));
        documents.forEach(document -> timeline.add(new EquipmentNodeLifecycleDto.TimelineItem(
                "DOCUMENT",
                document.getId(),
                document.getTitle(),
                document.getType() == null ? null : document.getType().name(),
                document.getRevision(),
                occurredAt(document),
                document.getCreatedAt()
        )));
        return timeline.stream()
                .filter(item -> item.occurredAt() != null)
                .sorted(Comparator.comparing(EquipmentNodeLifecycleDto.TimelineItem::occurredAt).reversed())
                .limit(limit)
                .toList();
    }

    private Instant occurredAt(com.toir.entity.BaseEntity entity) {
        return entity.getUpdatedAt() == null ? entity.getCreatedAt() : entity.getUpdatedAt();
    }

    private Map<UUID, TechnicalDocumentDto.FileRef> buildFileRefs(List<TechnicalDocument> documents) {
        List<UUID> fileIds = documents.stream()
                .map(TechnicalDocument::getFileId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new))
                .stream()
                .toList();
        if (fileIds.isEmpty()) {
            return Map.of();
        }
        return fileAssetRepository.findAllByIdInAndIsDeletedFalse(fileIds).stream()
                .collect(Collectors.toMap(FileAsset::getId, this::toFileRef, (a, b) -> a));
    }

    private TechnicalDocumentDto.FileRef fileRefFor(TechnicalDocument document,
                                                    Map<UUID, TechnicalDocumentDto.FileRef> fileRefsById) {
        if (document.getFileId() == null) {
            return null;
        }
        return fileRefsById.get(document.getFileId());
    }

    private TechnicalDocumentDto.FileRef toFileRef(FileAsset fileAsset) {
        return new TechnicalDocumentDto.FileRef(
                fileAsset.getId(),
                fileAsset.getFileName(),
                fileAsset.getOriginalName(),
                fileAsset.getMimeType(),
                fileAsset.getSizeBytes(),
                "/api/v1/files/assets/%s/download".formatted(fileAsset.getId())
        );
    }
}
