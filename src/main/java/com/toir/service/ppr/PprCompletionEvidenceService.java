package com.toir.service.ppr;

import com.toir.dto.workorder.CompleteWorkOrderRequest;
import com.toir.dto.workorder.WorkOrderCompletionEvidenceRequest;
import com.toir.entity.FileAsset;
import com.toir.entity.PprTask;
import com.toir.entity.WorkOrderCompletionEvidence;
import com.toir.entity.maintenance.WorkOrder;
import com.toir.enums.CompletionEvidenceType;
import com.toir.exception.RestException;
import com.toir.repository.FileAssetRepository;
import com.toir.repository.PprTaskRepository;
import com.toir.repository.WorkOrderCompletionEvidenceRepository;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PprCompletionEvidenceService {

    private final PprTaskRepository tasks;
    private final WorkOrderCompletionEvidenceRepository evidence;
    private final FileAssetRepository files;

    @Transactional
    public void validateAndStore(WorkOrder workOrder, CompleteWorkOrderRequest request, UUID actorId) {
        PprTask task = linkedTask(workOrder);
        if (task == null || task.getRequiredEvidenceTypes() == null
                || task.getRequiredEvidenceTypes().isEmpty()) {
            return;
        }

        Set<CompletionEvidenceType> satisfied = evidence
                .findAllByWorkOrderIdAndIsDeletedFalse(workOrder.getId())
                .stream()
                .map(WorkOrderCompletionEvidence::getEvidenceType)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(CompletionEvidenceType.class)));

        if (request.meterSnapshots() != null && !request.meterSnapshots().isEmpty()) {
            satisfied.add(CompletionEvidenceType.MEASUREMENT);
            if (task.getRequiredEvidenceTypes().contains(CompletionEvidenceType.MEASUREMENT)
                    && !evidence.existsByWorkOrderIdAndEvidenceTypeAndFileAssetIdAndIsDeletedFalse(
                    workOrder.getId(), CompletionEvidenceType.MEASUREMENT, null)) {
                WorkOrderCompletionEvidence measurement = new WorkOrderCompletionEvidence();
                measurement.setWorkOrderId(workOrder.getId());
                measurement.setPprTaskId(task.getId());
                measurement.setEvidenceType(CompletionEvidenceType.MEASUREMENT);
                measurement.setCapturedAt(request.performedAt() == null ? Instant.now() : request.performedAt());
                measurement.setSubmittedById(actorId);
                measurement.setNote("Meter snapshot recorded on completion");
                evidence.save(measurement);
            }
        }
        if (workOrder.getRepairActFileAssetId() != null || request.repairActFileId() != null) {
            satisfied.add(CompletionEvidenceType.REPAIR_ACT);
        }
        if (workOrder.getStoppageActFileAssetId() != null || request.stoppageActFileId() != null) {
            satisfied.add(CompletionEvidenceType.STOPPAGE_ACT);
        }

        for (WorkOrderCompletionEvidenceRequest submitted : safe(request.completionEvidence())) {
            if (submitted == null || submitted.type() == null) {
                throw RestException.badRequest("Completion evidence type is required");
            }
            FileAsset file = validateFile(submitted, workOrder);
            if (submitted.type() == CompletionEvidenceType.MEASUREMENT
                    && file == null
                    && (request.meterSnapshots() == null || request.meterSnapshots().isEmpty())) {
                throw RestException.badRequest("MEASUREMENT evidence requires a meter snapshot or file");
            }
            satisfied.add(submitted.type());
            if (!evidence.existsByWorkOrderIdAndEvidenceTypeAndFileAssetIdAndIsDeletedFalse(
                    workOrder.getId(), submitted.type(), submitted.fileAssetId())) {
                WorkOrderCompletionEvidence item = new WorkOrderCompletionEvidence();
                item.setWorkOrderId(workOrder.getId());
                item.setPprTaskId(task.getId());
                item.setEvidenceType(submitted.type());
                item.setFileAssetId(submitted.fileAssetId());
                item.setCapturedAt(submitted.capturedAt() == null ? Instant.now() : submitted.capturedAt());
                item.setSubmittedById(actorId);
                item.setNote(normalize(submitted.note()));
                evidence.save(item);
            }
        }

        Set<CompletionEvidenceType> missing = EnumSet.copyOf(task.getRequiredEvidenceTypes());
        missing.removeAll(satisfied);
        if (!missing.isEmpty()) {
            throw RestException.badRequest("Missing required PPR completion evidence: " + missing);
        }
    }

    @Transactional(readOnly = true)
    public Set<CompletionEvidenceType> missingEvidence(WorkOrder workOrder) {
        PprTask task = linkedTask(workOrder);
        if (task == null || task.getRequiredEvidenceTypes() == null
                || task.getRequiredEvidenceTypes().isEmpty()) {
            return Set.of();
        }
        Set<CompletionEvidenceType> satisfied = evidence
                .findAllByWorkOrderIdAndIsDeletedFalse(workOrder.getId())
                .stream()
                .map(WorkOrderCompletionEvidence::getEvidenceType)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(CompletionEvidenceType.class)));
        if (workOrder.getRepairActFileAssetId() != null) {
            satisfied.add(CompletionEvidenceType.REPAIR_ACT);
        }
        if (workOrder.getStoppageActFileAssetId() != null) {
            satisfied.add(CompletionEvidenceType.STOPPAGE_ACT);
        }
        Set<CompletionEvidenceType> missing = EnumSet.copyOf(task.getRequiredEvidenceTypes());
        missing.removeAll(satisfied);
        return Set.copyOf(missing);
    }

    private PprTask linkedTask(WorkOrder workOrder) {
        if (workOrder == null || workOrder.getPprTaskId() == null) {
            return null;
        }
        return tasks.findByIdAndIsDeletedFalse(workOrder.getPprTaskId())
                .orElseThrow(() -> RestException.notFound(
                        "PPR task not found: " + workOrder.getPprTaskId()));
    }

    private FileAsset validateFile(
            WorkOrderCompletionEvidenceRequest submitted,
            WorkOrder workOrder) {
        if (submitted.fileAssetId() == null) {
            if (submitted.type() == CompletionEvidenceType.MEASUREMENT) {
                return null;
            }
            throw RestException.badRequest(submitted.type() + " evidence requires a file");
        }
        FileAsset file = files.findByIdAndIsDeletedFalse(submitted.fileAssetId())
                .orElseThrow(() -> RestException.notFound(
                        "Evidence file not found: " + submitted.fileAssetId()));
        if (!"WORK_ORDER".equalsIgnoreCase(file.getEntityType())
                || !workOrder.getId().toString().equals(file.getEntityId())) {
            throw RestException.badRequest(
                    "Evidence file does not belong to work order " + workOrder.getId());
        }
        if ((submitted.type() == CompletionEvidenceType.BEFORE_PHOTO
                || submitted.type() == CompletionEvidenceType.AFTER_PHOTO)
                && (file.getMimeType() == null || !file.getMimeType().startsWith("image/"))) {
            throw RestException.badRequest(submitted.type() + " evidence must be an image");
        }
        return file;
    }

    private List<WorkOrderCompletionEvidenceRequest> safe(
            List<WorkOrderCompletionEvidenceRequest> submitted) {
        return submitted == null ? List.of() : submitted;
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
