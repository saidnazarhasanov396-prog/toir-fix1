package com.toir.service.ppr;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.toir.dto.workorder.CompleteWorkOrderRequest;
import com.toir.dto.workorder.WorkOrderCompletionEvidenceRequest;
import com.toir.entity.FileAsset;
import com.toir.entity.PprTask;
import com.toir.entity.maintenance.WorkOrder;
import com.toir.enums.CompletionEvidenceType;
import com.toir.exception.RestException;
import com.toir.repository.FileAssetRepository;
import com.toir.repository.PprTaskRepository;
import com.toir.repository.WorkOrderCompletionEvidenceRepository;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PprCompletionEvidenceServiceTest {

    @Mock PprTaskRepository tasks;
    @Mock WorkOrderCompletionEvidenceRepository evidence;
    @Mock FileAssetRepository files;

    private PprCompletionEvidenceService service;
    private WorkOrder workOrder;
    private PprTask task;

    @BeforeEach
    void setUp() {
        service = new PprCompletionEvidenceService(tasks, evidence, files);
        task = new PprTask();
        task.setId(UUID.randomUUID());
        task.setRequiredEvidenceTypes(Set.of(CompletionEvidenceType.AFTER_PHOTO));
        workOrder = new WorkOrder();
        workOrder.setId(UUID.randomUUID());
        workOrder.setPprTaskId(task.getId());
        when(tasks.findByIdAndIsDeletedFalse(task.getId())).thenReturn(Optional.of(task));
    }

    @Test
    void blocksCompletionWhenRequiredAfterPhotoIsMissing() {
        when(evidence.findAllByWorkOrderIdAndIsDeletedFalse(workOrder.getId())).thenReturn(List.of());

        assertThatThrownBy(() -> service.validateAndStore(workOrder, request(List.of()), UUID.randomUUID()))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("AFTER_PHOTO");
    }

    @Test
    void storesValidPhotoAndAllowsCompletion() {
        UUID fileId = UUID.randomUUID();
        when(evidence.findAllByWorkOrderIdAndIsDeletedFalse(workOrder.getId())).thenReturn(List.of());
        FileAsset image = new FileAsset();
        image.setMimeType("image/jpeg");
        image.setEntityType("WORK_ORDER");
        image.setEntityId(workOrder.getId().toString());
        when(files.findByIdAndIsDeletedFalse(fileId)).thenReturn(Optional.of(image));
        when(evidence.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.validateAndStore(
                workOrder,
                request(List.of(new WorkOrderCompletionEvidenceRequest(
                        CompletionEvidenceType.AFTER_PHOTO, fileId, null, "После ремонта"))),
                UUID.randomUUID());

        verify(evidence).save(any());
    }

    @Test
    void rejectsEvidenceFileAttachedToAnotherWorkOrder() {
        UUID fileId = UUID.randomUUID();
        when(evidence.findAllByWorkOrderIdAndIsDeletedFalse(workOrder.getId())).thenReturn(List.of());
        FileAsset image = new FileAsset();
        image.setMimeType("image/jpeg");
        image.setEntityType("WORK_ORDER");
        image.setEntityId(UUID.randomUUID().toString());
        when(files.findByIdAndIsDeletedFalse(fileId)).thenReturn(Optional.of(image));

        assertThatThrownBy(() -> service.validateAndStore(
                workOrder,
                request(List.of(new WorkOrderCompletionEvidenceRequest(
                        CompletionEvidenceType.AFTER_PHOTO, fileId, null, null))),
                UUID.randomUUID()))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("does not belong to work order");
    }

    @Test
    void storesMeasurementEvidenceFromMeterSnapshotForCloseReadiness() {
        task.setRequiredEvidenceTypes(Set.of(CompletionEvidenceType.MEASUREMENT));
        when(evidence.findAllByWorkOrderIdAndIsDeletedFalse(workOrder.getId())).thenReturn(List.of());
        when(evidence.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var meterSnapshot = new com.toir.dto.workorder.CompletionMeterSnapshotRequest(
                UUID.randomUUID(), com.toir.enums.MeterType.ENGINE_HOURS, 10.0, java.time.Instant.now());
        CompleteWorkOrderRequest request = new CompleteWorkOrderRequest(
                "Выполнено", null, null, null, null, null, null, null,
                List.of(meterSnapshot), null, null, null, null, List.of());

        service.validateAndStore(workOrder, request, UUID.randomUUID());

        verify(evidence).save(argThat(item ->
                item.getEvidenceType() == CompletionEvidenceType.MEASUREMENT
                        && item.getFileAssetId() == null));
    }

    private CompleteWorkOrderRequest request(List<WorkOrderCompletionEvidenceRequest> submitted) {
        return new CompleteWorkOrderRequest(
                "Выполнено", null, null, null, null, null, null, null,
                null, null, null, null, null, submitted);
    }
}
