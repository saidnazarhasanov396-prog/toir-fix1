package com.toir.service.repair;

import com.toir.dto.repairrequest.RepairRequestDto;
import com.toir.entity.repair.RepairRequest;
import com.toir.enums.RequestStatus;
import com.toir.repository.LocationRepository;
import com.toir.repository.department.DepartmentRepository;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.repository.repair.RepairRequestRepository;
import com.toir.repository.users.UserRepository;
import com.toir.util.AuditBuilderService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RepairRequestServiceTest {

    @Mock
    RepairRequestRepository repository;

    @Mock
    EquipmentRepository equipmentRepository;

    @Mock
    DepartmentRepository departmentRepository;

    @Mock
    LocationRepository locationRepository;

    @Mock
    UserRepository userRepository;

    @Mock
    AuditBuilderService auditBuilderService;

    @InjectMocks
    RepairRequestService service;

    @Test
    void requestClarificationSetsNeedsClarificationAndClarificationReason() {
        UUID id = UUID.randomUUID();
        RepairRequest entity = repairRequest(id);
        entity.setRejectionReason("old rejection");

        stubFindSaveAndDtoLookups(id, entity);

        RepairRequestDto result = service.requestClarification(id, "Need serial number");

        assertThat(result.status()).isEqualTo(RequestStatus.NEEDS_CLARIFICATION);
        assertThat(result.clarificationReason()).isEqualTo("Need serial number");
    }

    @Test
    void requestClarificationClearsRejectionReason() {
        UUID id = UUID.randomUUID();
        RepairRequest entity = repairRequest(id);
        entity.setRejectionReason("must be cleared");

        stubFindSaveAndDtoLookups(id, entity);

        RepairRequestDto result = service.requestClarification(id, "Need more photos");

        assertThat(result.rejectionReason()).isNull();
        assertThat(entity.getRejectionReason()).isNull();
    }

    @Test
    void rejectSetsRejectedAndRejectionReason() {
        UUID id = UUID.randomUUID();
        RepairRequest entity = repairRequest(id);

        stubFindSaveAndDtoLookups(id, entity);

        RepairRequestDto result = service.reject(id, "Safety violation");

        assertThat(result.status()).isEqualTo(RequestStatus.REJECTED);
        assertThat(result.rejectionReason()).isEqualTo("Safety violation");
    }

    @Test
    void rejectClearsClarificationReason() {
        UUID id = UUID.randomUUID();
        RepairRequest entity = repairRequest(id);
        entity.setClarificationReason("must be cleared");

        stubFindSaveAndDtoLookups(id, entity);

        RepairRequestDto result = service.reject(id, "Invalid request");

        assertThat(result.clarificationReason()).isNull();
        assertThat(entity.getClarificationReason()).isNull();
    }

    @Test
    void findByIdMapsBothReasonFieldsCorrectly() {
        UUID id = UUID.randomUUID();
        RepairRequest entity = repairRequest(id);
        entity.setRejectionReason("Rejection reason");
        entity.setClarificationReason("Clarification reason");

        when(repository.findByIdAndIsDeletedFalse(id)).thenReturn(Optional.of(entity));
        stubDtoLookups(entity);

        RepairRequestDto result = service.findById(id);

        assertThat(result.rejectionReason()).isEqualTo("Rejection reason");
        assertThat(result.clarificationReason()).isEqualTo("Clarification reason");
    }

    private void stubFindSaveAndDtoLookups(UUID id, RepairRequest entity) {
        when(repository.findByIdAndIsDeletedFalse(id)).thenReturn(Optional.of(entity));
        when(repository.save(any(RepairRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));
        stubDtoLookups(entity);
    }

    private void stubDtoLookups(RepairRequest entity) {
        when(equipmentRepository.findByIdAndIsDeletedFalse(entity.getEquipmentId())).thenReturn(Optional.empty());
        when(departmentRepository.findByIdAndIsDeletedFalse(entity.getDepartmentId())).thenReturn(Optional.empty());
        when(userRepository.findByIdAndIsDeletedFalse(entity.getReporterId())).thenReturn(Optional.empty());
    }

    private RepairRequest repairRequest(UUID id) {
        RepairRequest entity = new RepairRequest();
        ReflectionTestUtils.setField(entity, "id", id);
        entity.setNumber("RR-001");
        entity.setTitle("Repair request");
        entity.setDescription("Initial description");
        entity.setEquipmentId(UUID.randomUUID());
        entity.setDepartmentId(UUID.randomUUID());
        entity.setReporterId(UUID.randomUUID());
        entity.setStatus(RequestStatus.OPEN);
        return entity;
    }
}
