package com.toir.integration.erpcommand;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.toir.dto.equipment.EquipmentCreateRequest;
import com.toir.dto.warehouse.StockReceiptCommand;
import com.toir.dto.workorder.CompleteWorkOrderRequest;
import com.toir.dto.workorder.WorkOrderRequest;
import com.toir.dto.workorder.WorkOrderSparePartRequirementRequest;
import com.toir.entity.BaseEntity;
import com.toir.entity.maintenance.WorkOrderSparePartRequirement;
import com.toir.entity.repair.RepairMaterialUsage;
import com.toir.enums.WarehouseStockStatus;
import com.toir.enums.WorkOrderSparePartRequirementStatus;
import com.toir.repository.WorkOrderRepository;
import com.toir.repository.SparePartRepository;
import com.toir.repository.WarehouseRepository;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.repository.equipment.EquipmentTypeRepository;
import com.toir.repository.maintenance.WorkOrderSparePartRequirementRepository;
import com.toir.repository.repair.RepairMaterialUsageRepository;
import com.toir.service.WorkOrderService;
import com.toir.service.equipment.EquipmentService;
import com.toir.service.maintanance.WorkOrderSparePartRequirementService;
import com.toir.service.warehouse.ToirStockService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class ToirErpModuleCommandExecutor implements ErpModuleCommandExecutor {
  private static final Set<String> TYPES = Set.of(
      "maintenance.equipment.create", "maintenance.work-order.create",
      "maintenance.work-order.start", "maintenance.work-order.complete",
      "maintenance.material-requirement.add", "maintenance.spare-parts.reserve",
      "maintenance.spare-parts.issue", "maintenance.material.use", "maintenance.material.return"
  );

  private final ObjectMapper json;
  private final EquipmentService equipment;
  private final WorkOrderService workOrders;
  private final WorkOrderSparePartRequirementService requirements;
  private final ToirStockService stock;
  private final EquipmentRepository equipmentRepository;
  private final WorkOrderRepository workOrderRepository;
  private final WorkOrderSparePartRequirementRepository requirementRepository;
  private final RepairMaterialUsageRepository usageRepository;
  private final SparePartRepository sparePartRepository;
  private final WarehouseRepository warehouseRepository;
  private final EquipmentTypeRepository equipmentTypeRepository;

  public ToirErpModuleCommandExecutor(
      ObjectMapper json, EquipmentService equipment, WorkOrderService workOrders,
      WorkOrderSparePartRequirementService requirements, ToirStockService stock,
      EquipmentRepository equipmentRepository, WorkOrderRepository workOrderRepository,
      WorkOrderSparePartRequirementRepository requirementRepository,
      RepairMaterialUsageRepository usageRepository, SparePartRepository sparePartRepository,
      WarehouseRepository warehouseRepository, EquipmentTypeRepository equipmentTypeRepository
  ) {
    this.json = json; this.equipment = equipment; this.workOrders = workOrders;
    this.requirements = requirements; this.stock = stock;
    this.equipmentRepository = equipmentRepository; this.workOrderRepository = workOrderRepository;
    this.requirementRepository = requirementRepository; this.usageRepository = usageRepository;
    this.sparePartRepository = sparePartRepository; this.warehouseRepository = warehouseRepository;
    this.equipmentTypeRepository = equipmentTypeRepository;
  }

  @Override public Set<String> supportedTypes() { return TYPES; }

  @Override
  public long currentRevision(ErpModuleCommandEnvelope command) {
    JsonNode p = command.payload();
    BaseEntity entity = switch (text(p, "datasetType")) {
      case "toir.equipment.v1" -> equipmentRepository.findById(requiredUuid(p, "ownerId")).orElseThrow(this::notFound);
      case "toir.material-requirements.v1" -> requirementRepository.findById(requiredUuid(p, "ownerId")).orElseThrow(this::notFound);
      default -> workOrderRepository.findById(requiredUuid(p, "ownerId")).orElseThrow(this::notFound);
    };
    return entity.getUpdatedAt() == null ? 0 : entity.getUpdatedAt().toEpochMilli();
  }

  @Override
  public void execute(ErpModuleCommandEnvelope command) {
    JsonNode p = command.payload();
    switch (command.commandType()) {
      case "maintenance.equipment.create" -> createEquipment(p);
      case "maintenance.work-order.create" -> createWorkOrder(p);
      case "maintenance.work-order.start" -> workOrders.start(requiredUuid(p, "ownerId"));
      case "maintenance.work-order.complete" -> completeWorkOrder(p);
      case "maintenance.material-requirement.add" -> addRequirement(p);
      case "maintenance.spare-parts.reserve" -> reserve(command, p);
      case "maintenance.spare-parts.issue" -> issue(command, p);
      case "maintenance.material.use" -> recordUsage(p);
      case "maintenance.material.return" -> returnMaterial(command, p);
      default -> throw invalid("ERP_COMMAND_TYPE_UNSUPPORTED");
    }
  }

  private void createEquipment(JsonNode p) {
    ObjectNode request = copy(p);
    request.put("inventoryNumber", firstText(p, "inventoryNumber", "code", UUID.randomUUID().toString()));
    request.put("equipmentTypeId", equipmentTypeId(p).toString());
    if (p.hasNonNull("ownerDepartmentId")) request.set("departmentId", p.get("ownerDepartmentId"));
    if (p.hasNonNull("ownerWarehouseId")) request.set("warehouseId", p.get("ownerWarehouseId"));
    String location = text(p, "location");
    request.remove("location");
    if (location != null && !request.hasNonNull("description")) request.put("description", location);
    equipment.create(json.convertValue(request, EquipmentCreateRequest.class));
  }

  private void createWorkOrder(JsonNode p) {
    ObjectNode request = copy(p);
    request.put("number", requiredText(p, "workOrderNumber"));
    request.put("title", firstText(p, "title", "failureDescription", "ERP maintenance work order"));
    request.put("equipmentId", requiredText(p, "ownerEquipmentId"));
    if (p.hasNonNull("ownerTechnicianId")) request.set("performerId", p.get("ownerTechnicianId"));
    request.put("type", firstText(p, "type", null, "REPAIR"));
    request.put("workType", firstText(p, "workType", null, "REPAIR"));
    request.put("startPlannedAt", firstText(p, "requestedAt", null, Instant.now().toString()));
    workOrders.create(json.convertValue(request, WorkOrderRequest.class));
  }

  private void completeWorkOrder(JsonNode p) {
    ObjectNode request = copy(p);
    request.put("result", firstText(p, "result", null, "COMPLETED"));
    request.put("summary", firstText(p, "summary", "note", "Completed from ERP"));
    workOrders.complete(requiredUuid(p, "ownerId"), json.convertValue(request, CompleteWorkOrderRequest.class));
  }

  private void addRequirement(JsonNode p) {
    requirements.createManual(requiredUuid(p, "ownerWorkOrderId"), new WorkOrderSparePartRequirementRequest(
        sparePartId(p), requiredDecimal(p, "quantity"),
        firstText(p, "unit", null, "UNIT"), text(p, "criticality"), text(p, "note")));
  }

  private void reserve(ErpModuleCommandEnvelope command, JsonNode p) {
    int index = 0;
    for (WorkOrderSparePartRequirement requirement : selectedRequirements(p)) {
      stock.reserve(warehouseId(p, requirement), requirement.getSparePartId(),
          uuid(p, "ownerBinId"), quantity(p, requirement), "WORK_ORDER",
          requiredUuid(p, "ownerWorkOrderId"), text(p, "workOrderNumber"),
          command.idempotencyKey() + ":" + index++);
    }
  }

  private void issue(ErpModuleCommandEnvelope command, JsonNode p) {
    int index = 0;
    for (WorkOrderSparePartRequirement requirement : selectedRequirements(p)) {
      stock.fulfillReservation(warehouseId(p, requirement), requirement.getSparePartId(),
          uuid(p, "ownerBinId"), text(p, "batchNumber"), text(p, "serialNumber"), null,
          WarehouseStockStatus.AVAILABLE, quantity(p, requirement), "WORK_ORDER",
          requiredUuid(p, "ownerWorkOrderId"), text(p, "workOrderNumber"),
          command.idempotencyKey() + ":" + index++);
    }
  }

  private void recordUsage(JsonNode p) {
    WorkOrderSparePartRequirement requirement = selectedRequirement(p);
    RepairMaterialUsage usage = new RepairMaterialUsage();
    usage.setWorkOrderId(requiredUuid(p, "ownerWorkOrderId"));
    usage.setWarehouseId(warehouseId(p, requirement));
    usage.setSparePartId(requirement.getSparePartId());
    usage.setQuantity(requiredDecimal(p, "quantity"));
    usage.setBinId(uuid(p, "ownerBinId"));
    usage.setLotNumber(text(p, "batchNumber"));
    usage.setSerialNumber(text(p, "serialNumber"));
    usage.setIssuedAt(Instant.now());
    usage.setNotes(text(p, "note"));
    UUID requirementId = requirement.getId();
    usage.setRequirementId(requirementId);
    usageRepository.save(usage);
    requirement.setStatus(WorkOrderSparePartRequirementStatus.ISSUED);
    requirementRepository.save(requirement);
  }

  private void returnMaterial(ErpModuleCommandEnvelope command, JsonNode p) {
    WorkOrderSparePartRequirement requirement = selectedRequirement(p);
    stock.postReceipt(new StockReceiptCommand(
        warehouseId(p, requirement), requirement.getSparePartId(), uuid(p, "ownerBinId"),
        requiredDecimal(p, "quantity"), decimal(p, "unitCost", null), text(p, "batchNumber"),
        text(p, "serialNumber"), null, WarehouseStockStatus.AVAILABLE, "WORK_ORDER_RETURN",
        requiredUuid(p, "ownerWorkOrderId"), text(p, "workOrderNumber"), text(p, "note"), command.idempotencyKey()));
  }

  private UUID equipmentTypeId(JsonNode p) {
    UUID explicit = uuid(p, "ownerEquipmentTypeId");
    if (explicit != null) return explicit;
    return equipmentTypeRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc().stream()
        .map(BaseEntity::getId).findFirst()
        .orElseThrow(() -> invalid("ERP_COMMAND_EQUIPMENT_TYPE_NOT_CONFIGURED"));
  }

  private UUID sparePartId(JsonNode p) {
    UUID explicit = uuid(p, "ownerSparePartId");
    if (explicit != null) return explicit;
    String code = firstText(p, "productCode", "sparePartCode", null);
    if (code == null) throw invalid("ERP_COMMAND_SPARE_PART_REFERENCE_REQUIRED");
    return sparePartRepository.findFirstByCodeIgnoreCaseAndIsDeletedFalse(code)
        .map(BaseEntity::getId)
        .orElseThrow(() -> invalid("ERP_COMMAND_SPARE_PART_NOT_FOUND"));
  }

  private java.util.List<WorkOrderSparePartRequirement> selectedRequirements(JsonNode p) {
    UUID requirementId = uuid(p, "ownerRequirementId");
    if (requirementId != null) return java.util.List.of(requirementRepository.findById(requirementId)
        .filter(row -> !row.isDeleted()).orElseThrow(this::notFound));
    java.util.List<WorkOrderSparePartRequirement> rows = requirementRepository
        .findActiveByWorkOrderId(requiredUuid(p, "ownerWorkOrderId"));
    if (rows.isEmpty()) throw invalid("ERP_COMMAND_MATERIAL_REQUIREMENTS_EMPTY");
    return rows;
  }

  private WorkOrderSparePartRequirement selectedRequirement(JsonNode p) {
    UUID id = requiredUuid(p, "ownerRequirementId");
    return requirementRepository.findByIdAndWorkOrderIdAndIsDeletedFalse(
        id, requiredUuid(p, "ownerWorkOrderId")).orElseThrow(this::notFound);
  }

  private UUID warehouseId(JsonNode p, WorkOrderSparePartRequirement requirement) {
    UUID explicit = uuid(p, "ownerWarehouseId");
    if (explicit != null) return explicit;
    if (requirement.getWarehouseId() != null) return requirement.getWarehouseId();
    var workOrder = workOrderRepository.findById(requiredUuid(p, "ownerWorkOrderId")).orElseThrow(this::notFound);
    if (workOrder.getWarehouseId() != null) return workOrder.getWarehouseId();
    return warehouseRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc().stream()
        .map(BaseEntity::getId).findFirst()
        .orElseThrow(() -> invalid("ERP_COMMAND_WAREHOUSE_NOT_CONFIGURED"));
  }

  private BigDecimal quantity(JsonNode p, WorkOrderSparePartRequirement requirement) {
    BigDecimal explicit = decimal(p, "quantity", null);
    return explicit == null ? requirement.getRequiredQty() : explicit;
  }

  private ObjectNode copy(JsonNode source) { return source.deepCopy(); }
  private UUID requiredUuid(JsonNode n, String field) {
    UUID value = uuid(n, field); if (value == null) throw invalid("ERP_COMMAND_FIELD_INVALID"); return value;
  }
  private UUID uuid(JsonNode n, String field) {
    String value = text(n, field); if (value == null) return null;
    try { return UUID.fromString(value); } catch (IllegalArgumentException exception) { throw invalid("ERP_COMMAND_FIELD_INVALID"); }
  }
  private BigDecimal requiredDecimal(JsonNode n, String field) {
    BigDecimal value = decimal(n, field, null);
    if (value == null || value.signum() <= 0) throw invalid("ERP_COMMAND_FIELD_INVALID"); return value;
  }
  private BigDecimal decimal(JsonNode n, String field, BigDecimal fallback) {
    JsonNode value = n.path(field); return value.isNumber() ? value.decimalValue() : fallback;
  }
  private String requiredText(JsonNode n, String field) {
    String value = text(n, field); if (value == null) throw invalid("ERP_COMMAND_FIELD_INVALID"); return value;
  }
  private String text(JsonNode n, String field) {
    if (field == null) return null; String value = n.path(field).asText(null);
    return value == null || value.isBlank() ? null : value.trim();
  }
  private String firstText(JsonNode n, String first, String second, String fallback) {
    String value = text(n, first); if (value == null) value = text(n, second); return value == null ? fallback : value;
  }
  private ResponseStatusException notFound() { return invalid("ERP_COMMAND_AGGREGATE_NOT_FOUND"); }
  private ResponseStatusException invalid(String code) {
    return new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, code);
  }
}
