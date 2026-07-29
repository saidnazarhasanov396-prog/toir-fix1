package com.toir.dto.pprplanning.calendar;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.toir.enums.ApprovalStatus;
import com.toir.enums.EquipmentStatus;
import com.toir.enums.MaintenanceKind;
import com.toir.enums.PlanStatus;
import com.toir.enums.PprTaskStatus;
import com.toir.enums.PriorityLevel;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PprEquipmentCalendarResponseContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void serializesTheRequiredPublicFieldNamesAndAllTwelveMonthKeys() {
        PprEquipmentCalendarNamedRef namedRef =
                new PprEquipmentCalendarNamedRef(UUID.randomUUID(), "REF-1", "Reference");
        PprEquipmentCalendarPlanSummary plan = new PprEquipmentCalendarPlanSummary(
                UUID.randomUUID(), "PPR-2026-0001", "Annual plan", PlanStatus.DRAFT,
                ApprovalStatus.PENDING, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));
        PprEquipmentCalendarEquipment equipment = new PprEquipmentCalendarEquipment(
                UUID.randomUUID(), "EQ-1", "Pump", "INV-1", "TECH-1", EquipmentStatus.ACTIVE,
                true, "A", namedRef, namedRef, namedRef, namedRef, namedRef);
        PprEquipmentCalendarOccurrence occurrence = new PprEquipmentCalendarOccurrence(
                PprEquipmentCalendarOccurrenceSourceType.REGULATION,
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "REG-1", "Monthly regulation", MaintenanceKind.PREVENTIVE,
                "ТО", "Техническое обслуживание", LocalDate.of(2026, 1, 5),
                LocalDateTime.of(2026, 1, 5, 9, 0), LocalDateTime.of(2026, 1, 5, 11, 0),
                LocalDateTime.of(2026, 1, 5, 11, 0), PprTaskStatus.PLANNED,
                PriorityLevel.MEDIUM, new BigDecimal("2.00"), "Pump maintenance");
        PprEquipmentCalendarEquipmentRow row = new PprEquipmentCalendarEquipmentRow(
                equipment, 1, Map.of(1, List.of(occurrence)));
        PprEquipmentCalendarResponse response = new PprEquipmentCalendarResponse(
                plan, 2026, PprEquipmentCalendarAuthoritativeSource.CALCULATION_SNAPSHOT, 7L,
                PprEquipmentCalendarPlacementBasis.PLANNED_DATE,
                PprEquipmentCalendarSpanMode.START_MONTH,
                new PprEquipmentCalendarPageMetadata(0, 25, 1, 1), List.of(row),
                new PprEquipmentCalendarExcludedDiagnostics(1, 2, 3, 4));

        JsonNode json = objectMapper.valueToTree(response);

        assertThat(fieldNames(json)).containsExactlyInAnyOrder(
                "plan", "year", "authoritativeSource", "sourceRevision", "placementBasis",
                "spanMode", "page", "content", "excluded");
        assertThat(fieldNames(json.get("plan"))).containsExactlyInAnyOrder(
                "id", "code", "name", "status", "approvalStatus", "fromDate", "toDate");
        assertThat(fieldNames(json.at("/content/0"))).containsExactlyInAnyOrder(
                "equipment", "yearTaskCount", "months");
        assertThat(fieldNames(json.at("/content/0/equipment"))).containsExactlyInAnyOrder(
                "id", "code", "name", "inventoryNumber", "technicalNumber", "status",
                "deleted", "criticalityCode", "physicalDepartment", "responsibleDepartment",
                "parent", "location", "equipmentType");
        assertThat(json.at("/content/0/equipment/deleted").asBoolean()).isTrue();
        assertThat(fieldNames(json.at("/content/0/months"))).containsExactlyInAnyOrder(
                "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12");
        assertThat(fieldNames(json.at("/content/0/months/1/0"))).containsExactlyInAnyOrder(
                "sourceType", "taskId", "calculationItemId", "regulationId", "maintenanceRuleId",
                "sourceCode", "sourceName", "maintenanceKind", "displayCode", "displayName",
                "plannedDate", "scheduledStart", "scheduledEnd", "dueDate", "status", "priority",
                "plannedLaborHours", "title");
        assertThat(fieldNames(json.get("page"))).containsExactlyInAnyOrder(
                "number", "size", "totalElements", "totalPages");
        assertThat(fieldNames(json.get("excluded"))).containsExactlyInAnyOrder(
                "missingEquipment", "unresolvedEquipment", "outsidePlanYear", "outsidePlanRange");
        assertThat(json.at("/content/0/months/1/0/sourceType").asText()).isEqualTo("REGULATION");
    }

    private Set<String> fieldNames(JsonNode node) {
        Set<String> names = new LinkedHashSet<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }
}
