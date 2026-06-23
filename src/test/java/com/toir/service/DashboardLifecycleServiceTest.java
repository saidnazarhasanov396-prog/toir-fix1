package com.toir.service;

import com.toir.dto.dashboard.EquipmentLifecycleSummaryResponse;
import com.toir.dto.rcm.EquipmentRiskScore;
import com.toir.entity.equipment.Equipment;
import com.toir.enums.EquipmentLifecycleStage;
import com.toir.enums.EquipmentStatus;
import com.toir.repository.equipment.EquipmentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardLifecycleServiceTest {

    @Mock
    EquipmentRepository equipmentRepository;

    @Mock
    RcmService rcmService;

    @InjectMocks
    DashboardLifecycleService service;

    // ═══════════════════════════════════════════════════════
    // classify() — status ustuvor
    // ═══════════════════════════════════════════════════════

    @Test
    void decommissionedStatus_returnsDecommissioned() {
        UUID id = UUID.randomUUID();
        Equipment eq = equipment(id, EquipmentStatus.DECOMMISSIONED);

        mockData(List.of(eq), List.of(riskScore(id, 90))); // score yuqori bo'lsa ham

        EquipmentLifecycleSummaryResponse result = service.getLifecycleSummary();

        assertThat(result.decommissionedCount()).isEqualTo(1);
        assertThat(result.highRiskCount()).isEqualTo(0);
    }

    @Test
    void inRepairStatusWithLowRisk_returnsInRepair() {
        UUID id = UUID.randomUUID();
        Equipment eq = equipment(id, EquipmentStatus.IN_REPAIR);

        mockData(List.of(eq), List.of(riskScore(id, 0)));

        EquipmentLifecycleSummaryResponse result = service.getLifecycleSummary();

        assertThat(result.inRepairCount()).isEqualTo(1);
        assertThat(result.highRiskCount()).isEqualTo(0);
    }

    @Test
    void inRepairStatusWithHighRisk_returnsHighRisk() {
        UUID id = UUID.randomUUID();
        Equipment eq = equipment(id, EquipmentStatus.IN_REPAIR);

        mockData(List.of(eq), List.of(riskScore(id, 100)));

        EquipmentLifecycleSummaryResponse result = service.getLifecycleSummary();

        assertThat(result.highRiskCount()).isEqualTo(1);
        assertThat(result.inRepairCount()).isEqualTo(0);
        assertThat(result.highRiskEquipments()).hasSize(1);
        assertThat(result.highRiskEquipments().get(0).stage())
                .isEqualTo(EquipmentLifecycleStage.HIGH_RISK);
    }

    // ═══════════════════════════════════════════════════════
    // classify() — RCM score asosida
    // ═══════════════════════════════════════════════════════

    @Test
    void riskScore60Plus_returnsHighRisk() {
        UUID id = UUID.randomUUID();
        mockData(List.of(equipment(id, EquipmentStatus.ACTIVE)), List.of(riskScore(id, 60)));

        EquipmentLifecycleSummaryResponse result = service.getLifecycleSummary();

        assertThat(result.highRiskCount()).isEqualTo(1);
        assertThat(result.mediumRiskCount()).isEqualTo(0);
        assertThat(result.lowRiskCount()).isEqualTo(0);
    }

    @Test
    void riskScore59_returnsMediumRisk() {
        UUID id = UUID.randomUUID();
        mockData(List.of(equipment(id, EquipmentStatus.ACTIVE)), List.of(riskScore(id, 59)));

        EquipmentLifecycleSummaryResponse result = service.getLifecycleSummary();

        assertThat(result.mediumRiskCount()).isEqualTo(1);
        assertThat(result.highRiskCount()).isEqualTo(0);
    }

    @Test
    void riskScore30_returnsMediumRisk() {
        UUID id = UUID.randomUUID();
        mockData(List.of(equipment(id, EquipmentStatus.ACTIVE)), List.of(riskScore(id, 30)));

        EquipmentLifecycleSummaryResponse result = service.getLifecycleSummary();

        assertThat(result.mediumRiskCount()).isEqualTo(1);
    }

    @Test
    void riskScore29_returnsLowRisk() {
        UUID id = UUID.randomUUID();
        mockData(List.of(equipment(id, EquipmentStatus.ACTIVE)), List.of(riskScore(id, 29)));

        EquipmentLifecycleSummaryResponse result = service.getLifecycleSummary();

        assertThat(result.lowRiskCount()).isEqualTo(1);
        assertThat(result.mediumRiskCount()).isEqualTo(0);
    }

    @Test
    void riskScore0_returnsLowRisk() {
        UUID id = UUID.randomUUID();
        mockData(List.of(equipment(id, EquipmentStatus.ACTIVE)), List.of(riskScore(id, 0)));

        EquipmentLifecycleSummaryResponse result = service.getLifecycleSummary();

        assertThat(result.lowRiskCount()).isEqualTo(1);
    }

    @Test
    void noRcmScore_returnsLowRisk() {
        UUID id = UUID.randomUUID();
        mockData(List.of(equipment(id, EquipmentStatus.ACTIVE)), List.of()); // RCM yo'q

        EquipmentLifecycleSummaryResponse result = service.getLifecycleSummary();

        assertThat(result.lowRiskCount()).isEqualTo(1);
    }

    // ═══════════════════════════════════════════════════════
    // Counts va totals
    // ═══════════════════════════════════════════════════════

    @Test
    void totalCount_matchesAllEquipments() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        UUID id3 = UUID.randomUUID();

        mockData(
                List.of(
                        equipment(id1, EquipmentStatus.ACTIVE),
                        equipment(id2, EquipmentStatus.IN_REPAIR),
                        equipment(id3, EquipmentStatus.DECOMMISSIONED)
                ),
                List.of(riskScore(id1, 20))
        );

        EquipmentLifecycleSummaryResponse result = service.getLifecycleSummary();

        assertThat(result.totalCount()).isEqualTo(3);
        assertThat(result.inRepairCount()).isEqualTo(1);
        assertThat(result.decommissionedCount()).isEqualTo(1);
        assertThat(result.lowRiskCount()).isEqualTo(1);
    }

    @Test
    void emptyEquipments_returnsAllZeroCounts() {
        mockData(List.of(), List.of());

        EquipmentLifecycleSummaryResponse result = service.getLifecycleSummary();

        assertThat(result.totalCount()).isEqualTo(0);
        assertThat(result.highRiskCount()).isEqualTo(0);
        assertThat(result.mediumRiskCount()).isEqualTo(0);
        assertThat(result.lowRiskCount()).isEqualTo(0);
        assertThat(result.inRepairCount()).isEqualTo(0);
        assertThat(result.decommissionedCount()).isEqualTo(0);
        assertThat(result.highRiskEquipments()).isEmpty();
    }

    // ═══════════════════════════════════════════════════════
    // highRiskEquipments ro'yxati
    // ═══════════════════════════════════════════════════════

    @Test
    void highRiskEquipments_containsOnlyHighRiskOnes() {
        UUID highId   = UUID.randomUUID();
        UUID mediumId = UUID.randomUUID();

        mockData(
                List.of(
                        equipment(highId,   EquipmentStatus.ACTIVE),
                        equipment(mediumId, EquipmentStatus.ACTIVE)
                ),
                List.of(
                        riskScore(highId,   70),
                        riskScore(mediumId, 40)
                )
        );

        EquipmentLifecycleSummaryResponse result = service.getLifecycleSummary();

        assertThat(result.highRiskEquipments()).hasSize(1);
        assertThat(result.highRiskEquipments().get(0).equipmentId()).isEqualTo(highId);
        assertThat(result.highRiskEquipments().get(0).stage())
                .isEqualTo(EquipmentLifecycleStage.HIGH_RISK);
    }

    @Test
    void highRiskEquipments_sortedByRiskScoreDescending() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        UUID id3 = UUID.randomUUID();

        mockData(
                List.of(
                        equipment(id1, EquipmentStatus.ACTIVE),
                        equipment(id2, EquipmentStatus.ACTIVE),
                        equipment(id3, EquipmentStatus.ACTIVE)
                ),
                List.of(
                        riskScore(id1, 65),
                        riskScore(id2, 90),
                        riskScore(id3, 75)
                )
        );

        EquipmentLifecycleSummaryResponse result = service.getLifecycleSummary();

        assertThat(result.highRiskEquipments()).hasSize(3);
        assertThat(result.highRiskEquipments().get(0).riskScore()).isEqualTo(90);
        assertThat(result.highRiskEquipments().get(1).riskScore()).isEqualTo(75);
        assertThat(result.highRiskEquipments().get(2).riskScore()).isEqualTo(65);
    }

    @Test
    void highRiskEquipments_inRepairWithHighRiskIncluded() {
        UUID id = UUID.randomUUID();
        mockData(
                List.of(equipment(id, EquipmentStatus.IN_REPAIR)),
                List.of(riskScore(id, 90))
        );

        EquipmentLifecycleSummaryResponse result = service.getLifecycleSummary();

        assertThat(result.highRiskEquipments()).hasSize(1);
        assertThat(result.highRiskEquipments().get(0).equipmentId()).isEqualTo(id);
        assertThat(result.inRepairCount()).isEqualTo(0);
    }

    // ═══════════════════════════════════════════════════════
    // Helpers
    // ═══════════════════════════════════════════════════════

    private void mockData(List<Equipment> equipments, List<EquipmentRiskScore> scores) {
        when(equipmentRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc())
                .thenReturn(equipments);
        when(rcmService.computeAll()).thenReturn(scores);
    }

    private Equipment equipment(UUID id, EquipmentStatus status) {
        Equipment eq = new Equipment();
        eq.setId(id);
        eq.setCode("EQ-" + id.toString().substring(0, 4));
        eq.setName("Equipment " + id.toString().substring(0, 4));
        eq.setStatus(status);
        return eq;
    }

    private EquipmentRiskScore riskScore(UUID equipmentId, int score) {
        return new EquipmentRiskScore(
                equipmentId, "EQ-001", "Test Equipment",
                "A", 10, score / 10, score, 1, 2, 5000, 10
        );
    }
}