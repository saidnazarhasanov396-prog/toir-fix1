package com.toir.service.equipment;

import com.toir.dto.equipmentmanualattribute.BulkEquipmentManualAttributeRequest;
import com.toir.dto.equipmentmanualattribute.EquipmentManualAttributeRequest;
import com.toir.entity.equipment.Equipment;
import com.toir.entity.equipment.EquipmentManualAttribute;
import com.toir.exception.RestException;
import com.toir.repository.equipment.EquipmentManualAttributeRepository;
import com.toir.repository.equipment.EquipmentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EquipmentManualAttributeServiceTest {

    @Mock
    EquipmentRepository equipmentRepository;

    @Mock
    EquipmentManualAttributeRepository repository;

    @InjectMocks
    EquipmentManualAttributeService service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "writeEnabled", true);
    }

    @Test
    void createNormalizesKeyAndTrimsValue() {
        UUID equipmentId = UUID.randomUUID();
        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment(equipmentId)));
        when(repository.findByEquipmentIdAndKeyIgnoreCaseAndIsDeletedFalse(equipmentId, "ram"))
                .thenReturn(Optional.empty());
        when(repository.save(any(EquipmentManualAttribute.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.create(equipmentId, new EquipmentManualAttributeRequest(" RAM ", " 8GB "));

        ArgumentCaptor<EquipmentManualAttribute> captor = ArgumentCaptor.forClass(EquipmentManualAttribute.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getKey()).isEqualTo("ram");
        assertThat(captor.getValue().getValue()).isEqualTo("8GB");
    }

    @Test
    void reservedAndDuplicateKeysAreRejected() {
        UUID equipmentId = UUID.randomUUID();
        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment(equipmentId)));

        assertThatThrownBy(() -> service.create(equipmentId, new EquipmentManualAttributeRequest("status", "active")))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("reserved");

        assertThatThrownBy(() -> service.replaceAll(
                equipmentId,
                new BulkEquipmentManualAttributeRequest(List.of(
                        new EquipmentManualAttributeRequest("ram", "8GB"),
                        new EquipmentManualAttributeRequest(" RAM ", "16GB")
                ))
        ))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("Duplicate");
    }

    @Test
    void replaceAllUpdatesExistingAndSoftDeletesMissingRows() {
        UUID equipmentId = UUID.randomUUID();
        EquipmentManualAttribute ram = attribute(equipmentId, "ram", "4GB");
        EquipmentManualAttribute storage = attribute(equipmentId, "storage", "256GB");
        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment(equipmentId)));
        when(repository.findByEquipmentIdAndIsDeletedFalse(equipmentId)).thenReturn(List.of(ram, storage));
        when(repository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.replaceAll(equipmentId, new BulkEquipmentManualAttributeRequest(List.of(
                new EquipmentManualAttributeRequest(" RAM ", "8GB")
        )));

        assertThat(ram.getValue()).isEqualTo("8GB");
        assertThat(storage.isDeleted()).isTrue();
    }

    private static Equipment equipment(UUID id) {
        Equipment equipment = new Equipment();
        equipment.setId(id);
        return equipment;
    }

    private static EquipmentManualAttribute attribute(UUID equipmentId, String key, String value) {
        EquipmentManualAttribute attribute = new EquipmentManualAttribute();
        attribute.setId(UUID.randomUUID());
        attribute.setEquipmentId(equipmentId);
        attribute.setKey(key);
        attribute.setValue(value);
        return attribute;
    }
}
