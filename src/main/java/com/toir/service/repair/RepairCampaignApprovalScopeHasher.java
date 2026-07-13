package com.toir.service.repair;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.Objects;

import org.springframework.stereotype.Component;
import com.toir.entity.repair.RepairCampaign;
import com.toir.repository.PlannedShutdownRepository;
import com.toir.repository.plannedshutdown.PlannedShutdownCampaignLinkRepository;
import com.toir.repository.repair.RepairCampaignMaterialRequirementRepository;
import com.toir.repository.repair.RepairCampaignResourceAssignmentRepository;
import com.toir.repository.repair.RepairCampaignWorkDependencyRepository;
import com.toir.repository.repair.RepairCampaignWorkItemRepository;
import com.toir.repository.repair.RepairCampaignWorkItemWindowRepository;
import com.toir.repository.repair.RepairCampaignStageRepository;
import com.toir.repository.WorkOrderRepository;
import lombok.RequiredArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class RepairCampaignApprovalScopeHasher {

    private final RepairCampaignWorkItemRepository workItemRepository;
    private final RepairCampaignWorkDependencyRepository dependencyRepository;
    private final RepairCampaignResourceAssignmentRepository resourceRepository;
    private final RepairCampaignMaterialRequirementRepository materialRepository;
    private final PlannedShutdownCampaignLinkRepository shutdownLinkRepository;
    private final RepairCampaignWorkItemWindowRepository windowRepository;
    private final PlannedShutdownRepository plannedShutdownRepository;
    private final RepairCampaignStageRepository stageRepository;
    private final WorkOrderRepository workOrderRepository;

    public String hash(RepairCampaign campaign) {
        ArrayList<String> facts = new ArrayList<>();
        facts.add(fact("metadata", campaign.getCode(), campaign.getName(), campaign.getDepartmentId(),
                campaign.getCampaignType(), campaign.getResponsibleEmployeeId(), campaign.getPriority(),
                campaign.getObjective(), campaign.getScopeType(), campaign.getEquipmentTypeId(),
                campaign.getScope(), campaign.getNotes()));
        facts.add(fact("dates", campaign.getStartDate(), campaign.getEndDate()));
        facts.add(fact("budget", decimal(campaign.getTotalBudget()), campaign.getCurrencyCode(),
                campaign.getMaintenanceBudgetId()));
        facts.add(fact("scopeVersion", campaign.getScopeVersion()));
        campaign.getParticipantDepartments().stream()
                .sorted(Comparator.comparing(item -> item.getDepartmentId().toString()))
                .forEach(item -> facts.add(fact("participantDepartment", item.getDepartmentId(), item.getRole(),
                        decimal(item.getPlannedBudget()), item.getNotes())));
        stageRepository.findAllByCampaignIdOrderBySequenceAscIdAsc(campaign.getId()).forEach(stage ->
                facts.add(fact("stage", stage.getId(), stage.getSequence(), stage.getName(), stage.getStartDate(),
                        stage.getEndDate(), stage.getStatus(), decimal(stage.getPlannedCost()),
                        stage.getBudgetLineId(), stage.getUpdatedAt(), stage.isDeleted())));
        workOrderRepository.findAllByRepairCampaignIdAndIsDeletedFalseOrderByUpdatedAtDesc(campaign.getId()).stream()
                .sorted(Comparator.comparing(item -> item.getId().toString()))
                .forEach(item -> facts.add(fact("attachedWorkOrder", item.getId(), item.getRepairCampaignStageId(),
                        item.getEquipmentId(), item.getBudgetLineId(), item.getStatus(), item.getNumber())));

        workItemRepository.findAllByCampaignIdAndIsDeletedFalseOrderByOrderNumberAsc(campaign.getId())
                .forEach(item -> facts.add(fact("work", item.getId(), item.getSourceType(), item.getSourceId(),
                        item.getEquipmentId(), item.getTitle(), item.getPriority(), item.getStatus(),
                        item.getOrderNumber(), item.getNotes())));
        dependencyRepository.findAllByRepairCampaignIdAndIsDeletedFalseOrderByPredecessorWorkItemIdAscSuccessorWorkItemIdAsc(
                        campaign.getId())
                .forEach(item -> facts.add(fact("dependency", item.getId(), item.getPredecessorWorkItemId(),
                        item.getSuccessorWorkItemId())));
        resourceRepository.findAllByRepairCampaignIdAndIsDeletedFalseOrderByPlannedStartAtAscIdAsc(campaign.getId())
                .forEach(item -> facts.add(fact("resource", item.getId(), item.getWorkItemId(), item.getEmployeeId(),
                        item.getBrigadeId(), item.getCounteragentId(), item.getShiftCode(), item.getPlannedStartAt(),
                        item.getPlannedEndAt(), item.getCompetencyRequirement())));
        materialRepository.findAllByRepairCampaignIdAndIsDeletedFalseOrderByWorkItemIdAscSparePartIdAsc(campaign.getId())
                .forEach(item -> facts.add(fact("material", item.getId(), item.getWorkItemId(), item.getSparePartId(),
                        item.getWarehouseId(), decimal(item.getRequiredQuantity()), item.isCritical(),
                        item.isProcurementRequired())));

        var links = shutdownLinkRepository.findAllByRepairCampaignIdAndIsDeletedFalseOrderByPlannedShutdownId(campaign.getId());
        Map<java.util.UUID, com.toir.entity.PlannedShutdown> shutdowns = plannedShutdownRepository
                .findAllById(links.stream().map(link -> link.getPlannedShutdownId()).toList()).stream()
                .collect(Collectors.toMap(com.toir.entity.PlannedShutdown::getId, Function.identity()));
        links.forEach(link -> facts.add(fact("shutdown", link.getId(), link.getPlannedShutdownId(),
                shutdowns.containsKey(link.getPlannedShutdownId())
                        ? shutdowns.get(link.getPlannedShutdownId()).getWindowVersion() : null)));
        windowRepository.findAllByRepairCampaignIdAndIsDeletedFalseOrderByPlannedShutdownIdAscIdAsc(campaign.getId())
                .forEach(window -> facts.add(fact("window", window.getId(), window.getRepairCampaignWorkItemId(),
                        window.getPlannedShutdownId(), window.getShutdownWorkItemId(),
                        shutdowns.containsKey(window.getPlannedShutdownId())
                                ? shutdowns.get(window.getPlannedShutdownId()).getWindowVersion() : null)));
        return hashCanonicalFacts(facts);
    }

    private static String fact(String family, Object... values) {
        return family + ":" + java.util.Arrays.stream(values)
                .map(value -> value == null ? "<null>" : value.toString())
                .collect(Collectors.joining("|"));
    }

    private static String decimal(BigDecimal value) {
        return value == null ? "<null>" : value.stripTrailingZeros().toPlainString();
    }

    public static String hashCanonicalFacts(Collection<String> facts) {
        Objects.requireNonNull(facts, "facts");
        String canonical = facts.stream()
                .map(value -> value == null ? "<null>" : value)
                .sorted(Comparator.naturalOrder())
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
