package com.toir.service.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.toir.entity.ExternalEntityLink;
import com.toir.entity.equipment.Equipment;
import com.toir.entity.equipment.EquipmentStatusHistory;
import com.toir.entity.integration.AtilEquipmentStatusOutboxEvent;
import com.toir.enums.EquipmentStatus;
import com.toir.repository.ExternalEntityLinkRepository;
import com.toir.repository.integration.AtilEquipmentStatusOutboxRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AtilEquipmentStatusOutboxServiceTest {
    @Test
    void queuesCanonicalEventOnlyForMappedAtilVehicle() {
        AtilEquipmentStatusOutboxRepository outbox = mock(AtilEquipmentStatusOutboxRepository.class);
        ExternalEntityLinkRepository links = mock(ExternalEntityLinkRepository.class);
        when(outbox.existsByHistoryId(any())).thenReturn(false);
        UUID equipmentId = UUID.randomUUID(); UUID historyId = UUID.randomUUID(); UUID vehicleId = UUID.randomUUID();
        ExternalEntityLink link = new ExternalEntityLink(); link.setSourceSystem("ATIL"); link.setSourceEntityType("VEHICLE"); link.setSourceEntityId(vehicleId.toString());
        when(links.findBySourceSystemAndSourceEntityTypeAndTargetSystemAndTargetEntityTypeAndTargetEntityIdAndIsDeletedFalse(
                "ATIL", "VEHICLE", "TOIR", "EQUIPMENT", equipmentId)).thenReturn(Optional.of(link));
        Equipment equipment = new Equipment(); equipment.setId(equipmentId);
        EquipmentStatusHistory history = new EquipmentStatusHistory(); history.setId(historyId); history.setToStatus(EquipmentStatus.IN_REPAIR);
        history.setReason("Maintenance"); history.setChangedAt(Instant.parse("2026-07-17T12:00:00Z"));

        new AtilEquipmentStatusOutboxService(outbox, links, new ObjectMapper()).queue(equipment, history);

        ArgumentCaptor<AtilEquipmentStatusOutboxEvent> event = ArgumentCaptor.forClass(AtilEquipmentStatusOutboxEvent.class);
        verify(outbox).save(event.capture());
        assertThat(event.getValue().getPayload()).contains("\"target\":\"ATIL\"", "\"vehicleId\":\"" + vehicleId + "\"", "\"sourceStatus\":\"IN_REPAIR\"");
    }
}
