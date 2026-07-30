package com.toir.service.maintanance;

import com.toir.dto.maintenanceschedule.MaintenanceSchedulePreviewItem;
import com.toir.entity.PprPlan;
import com.toir.entity.maintenance.EquipmentMaintenanceRule;
import com.toir.entity.maintenance.MaintenanceRegulation;
import com.toir.entity.maintenance.MaintenanceScheduleCalculationItem;
import com.toir.enums.PriorityLevel;
import com.toir.exception.RestException;
import com.toir.repository.maintenance.EquipmentMaintenanceRuleRepository;
import com.toir.repository.maintenance.MaintenanceRegulationRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class MaintenanceScheduleSnapshotDraftFactory {

    private final EquipmentMaintenanceRuleRepository ruleRepository;
    private final MaintenanceRegulationRepository regulationRepository;
    private final PprTaskScheduleWindowCalculator scheduleWindowCalculator;
    private final MaintenanceScheduleSourceItemKeyGenerator sourceItemKeyGenerator;

    public MaintenanceScheduleSnapshotDraftFactory(
            EquipmentMaintenanceRuleRepository ruleRepository,
            MaintenanceRegulationRepository regulationRepository,
            PprTaskScheduleWindowCalculator scheduleWindowCalculator,
            MaintenanceScheduleSourceItemKeyGenerator sourceItemKeyGenerator) {
        this.ruleRepository = ruleRepository;
        this.regulationRepository = regulationRepository;
        this.scheduleWindowCalculator = scheduleWindowCalculator;
        this.sourceItemKeyGenerator = sourceItemKeyGenerator;
    }

    public List<MaintenanceScheduleCalculationItem> create(
            PprPlan plan,
            long revision,
            List<MaintenanceSchedulePreviewItem> previewItems) {
        Objects.requireNonNull(plan, "plan");
        if (revision < 1 || previewItems == null || previewItems.isEmpty()) {
            throw new IllegalArgumentException(
                    "Snapshot draft requires revision >= 1 and at least one preview item");
        }
        Map<UUID, EquipmentMaintenanceRule> rules = ruleRepository.findAllById(
                        previewItems.stream()
                                .map(MaintenanceSchedulePreviewItem::equipmentMaintenanceRuleId)
                                .filter(Objects::nonNull)
                                .distinct()
                                .toList())
                .stream()
                .collect(Collectors.toMap(
                        EquipmentMaintenanceRule::getId,
                        Function.identity()));
        Map<UUID, MaintenanceRegulation> regulations = regulationRepository.findAllById(
                        previewItems.stream()
                                .map(MaintenanceSchedulePreviewItem::regulationId)
                                .filter(Objects::nonNull)
                                .distinct()
                                .toList())
                .stream()
                .collect(Collectors.toMap(
                        MaintenanceRegulation::getId,
                        Function.identity()));

        long ordinal = 1L;
        java.util.ArrayList<MaintenanceScheduleCalculationItem> result =
                new java.util.ArrayList<>(previewItems.size());
        for (MaintenanceSchedulePreviewItem preview : previewItems) {
            PprTaskScheduleWindowCalculator.ScheduleWindow window =
                    scheduleWindowCalculator.calculate(
                            preview.plannedDate(),
                            preview.normativeLaborHours(),
                            plan.getStartDate(),
                            plan.getEndDate(),
                            plan.getExcludedWeekdays());
            EquipmentMaintenanceRule rule =
                    rules.get(preview.equipmentMaintenanceRuleId());
            MaintenanceRegulation regulation =
                    regulations.get(preview.regulationId());
            if ((preview.equipmentMaintenanceRuleId() != null && rule == null)
                    || (preview.equipmentMaintenanceRuleId() == null
                        && (preview.regulationId() == null
                            || regulation == null))) {
                throw new RestException(
                        "Maintenance schedule source metadata cannot be snapshotted",
                        org.springframework.http.HttpStatus.CONFLICT,
                        "PPR_CALCULATION_SOURCE_METADATA_MISSING");
            }
            String sourceCode = rule != null
                    ? rule.getCode()
                    : regulation == null ? null : regulation.getCode();
            String sourceName = rule != null
                    ? rule.getName()
                    : regulation == null ? null : regulation.getName();
            if (sourceCode == null || sourceCode.isBlank()
                    || sourceName == null || sourceName.isBlank()) {
                throw new RestException(
                        "Maintenance schedule source display metadata is incomplete",
                        org.springframework.http.HttpStatus.CONFLICT,
                        "PPR_CALCULATION_SOURCE_METADATA_MISSING");
            }
            MaintenanceScheduleCalculationItem item =
                    MaintenanceScheduleCalculationItem.builder()
                    .plan(plan)
                    .calculationRevision(revision)
                    .equipmentId(preview.equipmentId())
                    .regulationId(preview.regulationId())
                    .maintenanceRuleId(preview.equipmentMaintenanceRuleId())
                    .maintenanceType(preview.maintenanceKind())
                    .triggerDiscriminator(preview.anchorSource() == null
                            ? null
                            : preview.anchorSource().name())
                    .cycleOrdinal(ordinal++)
                    .plannedDate(preview.plannedDate())
                    .scheduledStart(window.scheduledStart())
                    .scheduledEnd(window.scheduledEnd())
                    .dueDate(window.dueDate())
                    .normativeLaborHours(
                            BigDecimal.valueOf(preview.normativeLaborHours()))
                    .priority(PriorityLevel.MEDIUM)
                    .departmentId(plan.getDepartmentId())
                    .equipmentCodeSnapshot(preview.equipmentCode())
                    .equipmentNameSnapshot(preview.equipmentName())
                    .regulationNameSnapshot(rule == null ? sourceName : null)
                    .maintenanceRuleNameSnapshot(rule == null ? null : sourceName)
                    .sourceCodeSnapshot(sourceCode)
                    .sourceNameSnapshot(sourceName)
                    .taskTitleSnapshot(sourceName + " — " + preview.equipmentCode())
                    .workOrderLeadDays(resolveWorkOrderLeadDays(rule, regulation))
                    .build();
            item.setSourceItemKey(sourceItemKeyGenerator.generate(
                    new MaintenanceScheduleSourceItemCoordinates(
                            item.getEquipmentId(),
                            item.getRegulationId(),
                            item.getMaintenanceRuleId(),
                            item.getTemplateId(),
                            item.getTriggerType(),
                            item.getTriggerDiscriminator(),
                            item.getMaintenanceType(),
                            item.getPlannedDate(),
                            item.getScheduledStart(),
                            item.getCycleOrdinal())));
            item.setSourceItemKeyVersion(sourceItemKeyGenerator.version());
            result.add(item);
        }
        return List.copyOf(result);
    }

    private static int resolveWorkOrderLeadDays(
            EquipmentMaintenanceRule rule,
            MaintenanceRegulation regulation) {
        if (rule != null && rule.getLeadTimeDays() != null) {
            return rule.getLeadTimeDays();
        }
        if (regulation != null && regulation.getLeadTimeDays() != null) {
            return regulation.getLeadTimeDays();
        }
        return 7;
    }
}
