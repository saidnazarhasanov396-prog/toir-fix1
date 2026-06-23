package com.toir.config;

import com.toir.entity.projects.ActualCost;
import com.toir.entity.projects.ActualCostReviewEvent;
import com.toir.repository.actualCost.ActualCostRepository;
import com.toir.repository.actualCost.ActualCostReviewEventRepository;
import com.toir.enums.ActualCostStatus;
import com.toir.entity.projects.BudgetLine;
import com.toir.repository.projects.BudgetLineRepository;
import com.toir.enums.BudgetStatus;
import com.toir.entity.projects.MaintenanceBudget;
import com.toir.repository.maintenance.MaintenanceBudgetRepository;
import com.toir.enums.CriticalityLevel;
import com.toir.enums.PriorityLevel;
import com.toir.entity.projects.CostCategory;
import com.toir.repository.CostCategoryRepository;
import com.toir.entity.equipment.CriticalityClass;
import com.toir.repository.CriticalityClassRepository;
import com.toir.entity.defects.Defect;
import com.toir.repository.defects.DefectRepository;
import com.toir.entity.defects.DefectList;
import com.toir.entity.defects.DefectListLine;
import com.toir.repository.defects.DefectListLineRepository;
import com.toir.repository.defects.DefectListRepository;
import com.toir.enums.DefectListStatus;
import com.toir.entity.defects.DefectCategory;
import com.toir.repository.defects.DefectCategoryRepository;
import com.toir.entity.defects.DefectSeverity;
import com.toir.repository.defects.DefectSeverityRepository;
import com.toir.entity.Department;
import com.toir.repository.department.DepartmentRepository;
import com.toir.enums.DepartmentType;
import com.toir.entity.DowntimeEvent;
import com.toir.repository.DowntimeEventRepository;
import com.toir.enums.DowntimeType;
import com.toir.entity.equipment.Equipment;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.enums.EquipmentStatus;
import com.toir.entity.equipment.EquipmentType;
import com.toir.repository.equipment.EquipmentTypeRepository;
import com.toir.entity.FailureReason;
import com.toir.repository.FailureReasonRepository;
import com.toir.enums.MaintenanceKind;
import com.toir.entity.maintenance.MaintenanceRegulation;
import com.toir.repository.maintenance.MaintenanceRegulationRepository;
import com.toir.enums.PeriodicityUnit;
import com.toir.entity.Notification;
import com.toir.enums.NotificationChannel;
import com.toir.repository.NotificationRepository;
import com.toir.enums.NotificationSeverity;
import com.toir.enums.NotificationStatus;
import com.toir.entity.ReliabilityMetric;
import com.toir.repository.ReliabilityMetricRepository;
import com.toir.entity.repair.RepairRequest;
import com.toir.repository.repair.RepairRequestRepository;
import com.toir.enums.RequestSource;
import com.toir.entity.RootCause;
import com.toir.repository.RootCauseRepository;
import com.toir.enums.InventoryItemKind;
import com.toir.entity.SparePart;
import com.toir.repository.SparePartRepository;
import com.toir.repository.SparePartTypeRepository;
import com.toir.entity.StockMovement;
import com.toir.repository.StockMovementRepository;
import com.toir.enums.StockMovementType;
import com.toir.entity.users.User;
import com.toir.repository.users.UserRepository;
import com.toir.entity.warehouse.Warehouse;
import com.toir.repository.WarehouseRepository;
import com.toir.dto.warehouse.StockReceiptCommand;
import com.toir.enums.StockLedgerMovementType;
import com.toir.service.warehouse.ToirStockService;
import com.toir.service.warehouse.LegacyStockProjectionService;
import com.toir.entity.maintenance.WorkOrder;
import com.toir.repository.WorkOrderRepository;
import com.toir.enums.WorkOrderStatus;
import com.toir.enums.WorkOrderType;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Random;
import java.util.UUID;

@Component
@Order(20)
@Transactional
@Profile("dev & demo-seed")
@RequiredArgsConstructor
public class SampleDataSeeder implements CommandLineRunner {

    private final DepartmentRepository departmentRepository;
    private final EquipmentTypeRepository equipmentTypeRepository;
    private final EquipmentRepository equipmentRepository;
    private final MaintenanceRegulationRepository regulationRepository;
    private final SparePartRepository sparePartRepository;
    private final SparePartTypeRepository sparePartTypeRepository;
    private final WarehouseRepository warehouseRepository;
    private final ToirStockService toirStockService;
    private final LegacyStockProjectionService legacyStockProjectionService;
    private final RepairRequestRepository repairRequestRepository;
    private final DefectRepository defectRepository;
    private final UserRepository userRepository;
    private final WorkOrderRepository workOrderRepository;
    private final DefectCategoryRepository defectCategoryRepository;
    private final DefectSeverityRepository defectSeverityRepository;
    private final FailureReasonRepository failureReasonRepository;
    private final RootCauseRepository rootCauseRepository;
    private final CriticalityClassRepository criticalityClassRepository;
    private final DowntimeEventRepository downtimeEventRepository;
    private final ReliabilityMetricRepository reliabilityMetricRepository;
    private final CostCategoryRepository costCategoryRepository;
    private final MaintenanceBudgetRepository budgetRepository;
    private final BudgetLineRepository budgetLineRepository;
    private final ActualCostRepository actualCostRepository;
    private final ActualCostReviewEventRepository actualCostReviewEventRepository;
    private final NotificationRepository notificationRepository;
    private final StockMovementRepository stockMovementRepository;
    private final DefectListRepository defectListRepository;
    private final DefectListLineRepository defectListLineRepository;

    @Override
    public void run(String... args) {
        if (departmentRepository.countByIsDeletedFalse() > 0) return;

        seedDictionaries();

        Department plant = saveDept("NAV", "АО Navoiyazot", "JSC Navoiyazot", "Navoiyazot AJ", DepartmentType.ENTERPRISE, null);
        Department ammonia = saveDept("NAV-AMM", "Цех аммиака", "Ammonia workshop", "Ammiak sexi", DepartmentType.WORKSHOP, plant.getId());
        Department urea = saveDept("NAV-UREA", "Цех карбамида", "Urea workshop", "Karbamid sexi", DepartmentType.WORKSHOP, plant.getId());
        Department nitric = saveDept("NAV-HNO3", "Цех азотной кислоты", "Nitric acid workshop", "Nitrat kislota sexi", DepartmentType.WORKSHOP, plant.getId());
        saveDept("NAV-AMM-S1", "Участок синтеза", "Synthesis section", "Sintez uchastkasi", DepartmentType.SECTION, ammonia.getId());
        saveDept("NAV-AMM-S2", "Участок компрессии", "Compression section", "Kompressiya uchastkasi", DepartmentType.SECTION, ammonia.getId());
        saveDept("NAV-UREA-S1", "Участок грануляции", "Granulation section", "Granulyatsiya uchastkasi", DepartmentType.SECTION, urea.getId());

        EquipmentType pump = saveType("PUMP-CENTR", "Насос центробежный", "Centrifugal pump", "Markazdan qochma nasos", "Динамическое оборудование");
        EquipmentType compressor = saveType("COMP-AXIAL", "Компрессор осевой", "Axial compressor", "O'q kompressori", "Динамическое оборудование");
        EquipmentType reactor = saveType("REACTOR", "Реактор колонный", "Column reactor", "Kolonna reaktor", "Статическое оборудование");
        EquipmentType heatExchanger = saveType("HEATEX", "Теплообменник", "Heat exchanger", "Issiqlik almashgichi", "Статическое оборудование");
        EquipmentType valve = saveType("VALVE", "Арматура запорная", "Shut-off valve", "Yopiq armatura", "Арматура");

        List<Equipment> all = List.of(
                saveEquipment("NAV-AMM-CMP-01", "Компрессор синтез-газа К-1", "INV-1001", compressor, ammonia, EquipmentStatus.ACTIVE),
                saveEquipment("NAV-AMM-CMP-02", "Компрессор синтез-газа К-2", "INV-1002", compressor, ammonia, EquipmentStatus.STANDBY),
                saveEquipment("NAV-AMM-PMP-01", "Насос конденсата Н-101", "INV-1010", pump, ammonia, EquipmentStatus.ACTIVE),
                saveEquipment("NAV-AMM-PMP-02", "Насос конденсата Н-102", "INV-1011", pump, ammonia, EquipmentStatus.ACTIVE),
                saveEquipment("NAV-AMM-PMP-03", "Насос охлаждения Н-103", "INV-1012", pump, ammonia, EquipmentStatus.IN_REPAIR),
                saveEquipment("NAV-AMM-RCT-01", "Реактор синтеза аммиака Р-1", "INV-1020", reactor, ammonia, EquipmentStatus.ACTIVE),
                saveEquipment("NAV-AMM-HE-01", "Теплообменник Т-101", "INV-1030", heatExchanger, ammonia, EquipmentStatus.ACTIVE),
                saveEquipment("NAV-AMM-HE-02", "Теплообменник Т-102", "INV-1031", heatExchanger, ammonia, EquipmentStatus.ACTIVE),
                saveEquipment("NAV-UREA-RCT-01", "Реактор карбамида Р-2", "INV-2001", reactor, urea, EquipmentStatus.ACTIVE),
                saveEquipment("NAV-UREA-PMP-01", "Насос плава Н-201", "INV-2010", pump, urea, EquipmentStatus.ACTIVE),
                saveEquipment("NAV-UREA-PMP-02", "Насос плава Н-202", "INV-2011", pump, urea, EquipmentStatus.STANDBY),
                saveEquipment("NAV-UREA-HE-01", "Теплообменник Т-201", "INV-2030", heatExchanger, urea, EquipmentStatus.ACTIVE),
                saveEquipment("NAV-UREA-VLV-01", "Клапан регулирующий К-201", "INV-2040", valve, urea, EquipmentStatus.ACTIVE),
                saveEquipment("NAV-HNO3-CMP-01", "Компрессор воздуха К-3", "INV-3001", compressor, nitric, EquipmentStatus.ACTIVE),
                saveEquipment("NAV-HNO3-CMP-02", "Компрессор воздуха К-4", "INV-3002", compressor, nitric, EquipmentStatus.ACTIVE),
                saveEquipment("NAV-HNO3-RCT-01", "Реактор окисления Р-3", "INV-3020", reactor, nitric, EquipmentStatus.ACTIVE),
                saveEquipment("NAV-HNO3-PMP-01", "Насос кислоты Н-301", "INV-3010", pump, nitric, EquipmentStatus.ACTIVE),
                saveEquipment("NAV-HNO3-PMP-02", "Насос кислоты Н-302", "INV-3011", pump, nitric, EquipmentStatus.ACTIVE),
                saveEquipment("NAV-HNO3-HE-01", "Теплообменник Т-301", "INV-3030", heatExchanger, nitric, EquipmentStatus.ACTIVE),
                saveEquipment("NAV-HNO3-HE-02", "Теплообменник Т-302", "INV-3031", heatExchanger, nitric, EquipmentStatus.ACTIVE),
                saveEquipment("NAV-HNO3-HE-03", "Теплообменник Т-303", "INV-3032", heatExchanger, nitric, EquipmentStatus.DECOMMISSIONED),
                saveEquipment("NAV-AMM-VLV-01", "Клапан предохранительный К-101", "INV-1040", valve, ammonia, EquipmentStatus.ACTIVE),
                saveEquipment("NAV-AMM-VLV-02", "Клапан предохранительный К-102", "INV-1041", valve, ammonia, EquipmentStatus.ACTIVE),
                saveEquipment("NAV-UREA-CMP-01", "Компрессор CO2 К-5", "INV-2050", compressor, urea, EquipmentStatus.ACTIVE),
                saveEquipment("NAV-UREA-VLV-02", "Клапан К-202", "INV-2041", valve, urea, EquipmentStatus.ACTIVE),
                saveEquipment("NAV-AMM-PMP-04", "Насос Н-104", "INV-1013", pump, ammonia, EquipmentStatus.ACTIVE),
                saveEquipment("NAV-AMM-PMP-05", "Насос Н-105", "INV-1014", pump, ammonia, EquipmentStatus.STANDBY),
                saveEquipment("NAV-HNO3-VLV-01", "Клапан К-301", "INV-3040", valve, nitric, EquipmentStatus.ACTIVE),
                saveEquipment("NAV-HNO3-VLV-02", "Клапан К-302", "INV-3041", valve, nitric, EquipmentStatus.ACTIVE),
                saveEquipment("NAV-UREA-HE-02", "Теплообменник Т-202", "INV-2031", heatExchanger, urea, EquipmentStatus.ACTIVE)
        );

        linkEquipmentCriticalityAndResponsible(all);

        saveRegulation("REG-PUMP-M", "ТО насоса ежемесячно", pump, MaintenanceKind.PREVENTIVE, 4, PeriodicityUnit.MONTH, 1);
        saveRegulation("REG-PUMP-Y", "Капитальный ремонт насоса", pump, MaintenanceKind.OVERHAUL, 40, PeriodicityUnit.YEAR, 1);
        saveRegulation("REG-COMP-Q", "ТО компрессора квартальное", compressor, MaintenanceKind.PREVENTIVE, 16, PeriodicityUnit.QUARTER, 1);
        saveRegulation("REG-COMP-Y", "Капитальный ремонт компрессора", compressor, MaintenanceKind.OVERHAUL, 120, PeriodicityUnit.YEAR, 1);
        saveRegulation("REG-REACTOR-Y", "Ревизия реактора", reactor, MaintenanceKind.INSPECTION, 80, PeriodicityUnit.YEAR, 1);
        saveRegulation("REG-REACTOR-5Y", "Капитальный ремонт реактора", reactor, MaintenanceKind.OVERHAUL, 400, PeriodicityUnit.YEAR, 5);
        saveRegulation("REG-HE-Q", "Чистка теплообменника", heatExchanger, MaintenanceKind.PREVENTIVE, 12, PeriodicityUnit.QUARTER, 1);
        saveRegulation("REG-HE-Y", "Гидроиспытания теплообменника", heatExchanger, MaintenanceKind.INSPECTION, 24, PeriodicityUnit.YEAR, 1);
        saveRegulation("REG-VLV-M", "Проверка арматуры", valve, MaintenanceKind.INSPECTION, 2, PeriodicityUnit.MONTH, 1);
        saveRegulation("REG-VLV-Y", "Ревизия арматуры", valve, MaintenanceKind.OVERHAUL, 8, PeriodicityUnit.YEAR, 1);

        SparePart bearing = saveSparePart("SP-BRG-6208", "Подшипник 6208", "шт", 20);
        SparePart seal = saveSparePart("SP-SEAL-AMM1", "Сальник торцевой", "шт", 10);
        SparePart gasket = saveSparePart("SP-GSK-DN100", "Прокладка DN100", "шт", 50);
        SparePart oilFilter = saveSparePart("SP-OILFLT-01", "Масляный фильтр", "шт", 30);
        saveSparePart("SP-BOLT-M20", "Болт M20", "шт", 500);
        saveSparePart("SP-NUT-M20", "Гайка M20", "шт", 500);
        saveSparePart("SP-VBELT-A1250", "Клиновой ремень A1250", "шт", 15);

        Warehouse mainWh = new Warehouse();
        mainWh.setCode("WH-MAIN");
        mainWh.setName("Центральный склад");
        mainWh.setDepartmentId(plant.getId());
        warehouseRepository.save(mainWh);

        Warehouse ammWh = new Warehouse();
        ammWh.setCode("WH-AMM");
        ammWh.setName("Склад цеха аммиака");
        ammWh.setDepartmentId(ammonia.getId());
        warehouseRepository.save(ammWh);

        // intentionally low stocks to demonstrate lowStockItems
        seedStock(mainWh.getId(), bearing.getId(), 48, 20);
        seedStock(mainWh.getId(), seal.getId(), 8, 10);    // low stock
        seedStock(mainWh.getId(), gasket.getId(), 140, 50);
        seedStock(mainWh.getId(), oilFilter.getId(), 25, 30); // low stock
        seedStock(ammWh.getId(), bearing.getId(), 12, 5);
        seedStock(ammWh.getId(), seal.getId(), 6, 3);

        UUID admin = userRepository.findByUsernameAndIsDeletedFalse("admin").map(User::getId).orElse(null);

        seedRequest(all.get(4), "RR-2026-0001", "Течь по сальнику Н-103", PriorityLevel.HIGH, CriticalityLevel.HIGH, admin);
        seedRequest(all.get(0), "RR-2026-0002", "Вибрация компрессора К-1", PriorityLevel.CRITICAL, CriticalityLevel.CRITICAL, admin);
        seedRequest(all.get(13), "RR-2026-0003", "Повышенная температура подшипника К-3", PriorityLevel.HIGH, CriticalityLevel.HIGH, admin);
        seedRequest(all.get(2), "RR-2026-0004", "Снижение производительности Н-101", PriorityLevel.MEDIUM, CriticalityLevel.MEDIUM, admin);
        seedRequest(all.get(6), "RR-2026-0005", "Загрязнение Т-101", PriorityLevel.LOW, CriticalityLevel.LOW, admin);
        seedRequest(all.get(15), "RR-2026-0006", "Течь охлаждения Р-3", PriorityLevel.EMERGENCY, CriticalityLevel.CRITICAL, admin);
        seedRequest(all.get(9), "RR-2026-0007", "Шум в Н-201", PriorityLevel.MEDIUM, CriticalityLevel.MEDIUM, admin);
        seedRequest(all.get(14), "RR-2026-0008", "Падение давления К-4", PriorityLevel.HIGH, CriticalityLevel.HIGH, admin);
        seedRequest(all.get(11), "RR-2026-0009", "Эрозия клапана К-201", PriorityLevel.MEDIUM, CriticalityLevel.MEDIUM, admin);
        seedRequest(all.get(18), "RR-2026-0010", "Загрязнение Т-301", PriorityLevel.LOW, CriticalityLevel.LOW, admin);
        seedRequest(all.get(7), "RR-2026-0011", "Снижение теплопередачи Т-102", PriorityLevel.MEDIUM, CriticalityLevel.MEDIUM, admin);
        seedRequest(all.get(8), "RR-2026-0012", "Утечка карбамида в реакторе Р-2", PriorityLevel.CRITICAL, CriticalityLevel.CRITICAL, admin);
        seedRequest(all.get(10), "RR-2026-0013", "Перегрев Н-202", PriorityLevel.HIGH, CriticalityLevel.HIGH, admin);
        seedRequest(all.get(16), "RR-2026-0014", "Снижение давления Н-301", PriorityLevel.MEDIUM, CriticalityLevel.MEDIUM, admin);
        seedRequest(all.get(17), "RR-2026-0015", "Шум в Н-302", PriorityLevel.LOW, CriticalityLevel.LOW, admin);
        seedRequest(all.get(19), "RR-2026-0016", "Загрязнение Т-302", PriorityLevel.LOW, CriticalityLevel.MEDIUM, admin);
        seedRequest(all.get(21), "RR-2026-0017", "Срыв клапана К-101", PriorityLevel.HIGH, CriticalityLevel.HIGH, admin);
        seedRequest(all.get(22), "RR-2026-0018", "Утечка через клапан К-102", PriorityLevel.MEDIUM, CriticalityLevel.MEDIUM, admin);
        seedRequest(all.get(23), "RR-2026-0019", "Шум в компрессоре К-5", PriorityLevel.HIGH, CriticalityLevel.HIGH, admin);
        seedRequest(all.get(29), "RR-2026-0020", "Засор Т-202", PriorityLevel.LOW, CriticalityLevel.LOW, admin);
        seedRequest(all.get(25), "RR-2026-0021", "Аномальная вибрация Н-104", PriorityLevel.HIGH, CriticalityLevel.HIGH, admin);
        seedRequest(all.get(26), "RR-2026-0022", "Перегрев Н-105", PriorityLevel.MEDIUM, CriticalityLevel.MEDIUM, admin);

        seedDefect(all.get(4), "DEF-2026-0001", "Износ сальника", "SEAL_WEAR", "MAJOR", "MECH_WEAR", "INSUFFICIENT_LUB");
        seedDefect(all.get(0), "DEF-2026-0002", "Износ подшипника ротора", "BEARING_WEAR", "CRITICAL", "MECH_WEAR", "DESIGN_DEFECT");
        seedDefect(all.get(13), "DEF-2026-0003", "Дефект подшипника компрессора", "BEARING_WEAR", "MAJOR", "MECH_WEAR", "MAINTENANCE_ERROR");
        seedDefect(all.get(15), "DEF-2026-0004", "Коррозия корпуса реактора", "CORROSION", "CRITICAL", "CORROSION", "MATERIAL_DEFECT");
        seedDefect(all.get(11), "DEF-2026-0005", "Эрозия седла клапана", "EROSION", "MINOR", "EROSION", "OPERATING_MODE");

        if (admin != null) {
            seedWorkOrder("WO-2026-0001", "Замена сальника Н-103", all.get(4), WorkOrderType.DEFECT, PriorityLevel.HIGH, WorkOrderStatus.CLOSED, admin, 6);
            seedWorkOrder("WO-2026-0002", "Балансировка ротора К-1", all.get(0), WorkOrderType.EMERGENCY, PriorityLevel.CRITICAL, WorkOrderStatus.IN_PROGRESS, admin, 0);
            seedWorkOrder("WO-2026-0003", "Замена подшипника К-3", all.get(13), WorkOrderType.DEFECT, PriorityLevel.HIGH, WorkOrderStatus.CLOSED, admin, 12);
            seedWorkOrder("WO-2026-0004", "ТО Н-101 (плановое)", all.get(2), WorkOrderType.PLANNED, PriorityLevel.MEDIUM, WorkOrderStatus.CLOSED, admin, 36);
            seedWorkOrder("WO-2026-0005", "Чистка Т-101", all.get(6), WorkOrderType.PLANNED, PriorityLevel.LOW, WorkOrderStatus.APPROVED, admin, 0);
            seedWorkOrder("WO-2026-0006", "Аварийный ремонт Р-3", all.get(15), WorkOrderType.EMERGENCY, PriorityLevel.EMERGENCY, WorkOrderStatus.CLOSED, admin, 18);
            seedWorkOrder("WO-2026-0007", "Замена прокладок Н-201", all.get(9), WorkOrderType.DEFECT, PriorityLevel.MEDIUM, WorkOrderStatus.CLOSED, admin, 4);
            seedWorkOrder("WO-2026-0008", "Калибровка К-4", all.get(14), WorkOrderType.INSPECTION, PriorityLevel.HIGH, WorkOrderStatus.IN_PROGRESS, admin, 0);
            seedWorkOrder("WO-2026-0009", "Ревизия клапана К-201", all.get(11), WorkOrderType.PLANNED, PriorityLevel.MEDIUM, WorkOrderStatus.PLANNED, admin, 0);
            seedWorkOrder("WO-2026-0010", "Чистка Т-301", all.get(18), WorkOrderType.PLANNED, PriorityLevel.LOW, WorkOrderStatus.CLOSED, admin, 8);
            seedWorkOrder("WO-2026-0011", "Промывка Т-102", all.get(7), WorkOrderType.PLANNED, PriorityLevel.MEDIUM, WorkOrderStatus.CLOSED, admin, 5);
            seedWorkOrder("WO-2026-0012", "Сварка корпуса Р-2", all.get(8), WorkOrderType.OVERHAUL, PriorityLevel.CRITICAL, WorkOrderStatus.CLOSED, admin, 48);
            seedWorkOrder("WO-2026-0013", "Замена подшипников Н-202", all.get(10), WorkOrderType.DEFECT, PriorityLevel.HIGH, WorkOrderStatus.CLOSED, admin, 6);
            seedWorkOrder("WO-2026-0014", "Регулировка Н-301", all.get(16), WorkOrderType.INSPECTION, PriorityLevel.MEDIUM, WorkOrderStatus.APPROVED, admin, 0);
            seedWorkOrder("WO-2026-0015", "Виброанализ Н-302", all.get(17), WorkOrderType.INSPECTION, PriorityLevel.LOW, WorkOrderStatus.DRAFT, admin, 0);
            seedWorkOrder("WO-2026-0016", "Очистка Т-302", all.get(19), WorkOrderType.PLANNED, PriorityLevel.LOW, WorkOrderStatus.CLOSED, admin, 4);
            seedWorkOrder("WO-2026-0017", "Замена клапана К-101", all.get(21), WorkOrderType.DEFECT, PriorityLevel.HIGH, WorkOrderStatus.CLOSED, admin, 3);
        }

        seedDowntimeEvents(all, plant, ammonia, urea, nitric);
        seedReliabilityMetrics(all);
        seedBudgetsAndCosts(plant, ammonia, urea, nitric);
        seedStockMovements(mainWh, ammWh, bearing, seal, gasket, oilFilter, admin);
        seedNotifications(admin);
        seedDefectLists(all, admin, bearing, seal);
    }

    private void linkEquipmentCriticalityAndResponsible(List<Equipment> all) {
        List<CriticalityClass> crits = criticalityClassRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc();
        CriticalityClass critical = crits.stream().filter(c -> "CRIT-CRIT".equals(c.getCode())).findFirst().orElse(null);
        CriticalityClass high = crits.stream().filter(c -> "CRIT-HIGH".equals(c.getCode())).findFirst().orElse(null);
        CriticalityClass medium = crits.stream().filter(c -> "CRIT-MED".equals(c.getCode())).findFirst().orElse(null);
        CriticalityClass low = crits.stream().filter(c -> "CRIT-LOW".equals(c.getCode())).findFirst().orElse(null);
        UUID admin = userRepository.findByUsernameAndIsDeletedFalse("admin").map(User::getId).orElse(null);

        for (Equipment eq : all) {
            CriticalityClass cls;
            if (eq.getCode().contains("RCT")) cls = critical;
            else if (eq.getCode().contains("CMP")) cls = high;
            else if (eq.getCode().contains("PMP") || eq.getCode().contains("HE")) cls = medium;
            else cls = low;
            if (cls != null) eq.setCriticalityClassId(cls.getId());
            if (admin != null) eq.setResponsibleId(admin);
            equipmentRepository.save(eq);
        }
    }

    private void seedDefectLists(List<Equipment> all, UUID admin, SparePart bearing, SparePart seal) {
        if (admin == null) return;

        // Defect list #1 — based on inspection of Н-103 (leaking seal)
        DefectList dl1 = new DefectList();
        dl1.setCode("DL-2026-0001");
        dl1.setTitle("Ведомость дефектов Н-103 (течь сальника)");
        dl1.setEquipmentId(all.get(4).getId());
        dl1.setCreatedById(admin);
        dl1.setApprovedById(admin);
        dl1.setStatus(DefectListStatus.APPROVED);
        dl1.setNotes("Составлено по результатам внеочередного осмотра");
        dl1 = defectListRepository.save(dl1);
        addDefectListLine(dl1, "Износ торцевого сальника", "Демонтаж, замена", "Сальник торцевой", seal.getId(), 2, 3.0, 85_000);
        addDefectListLine(dl1, "Задиры вала ротора", "Полировка шейки вала", "Полировочная паста", null, 1, 2.0, 25_000);
        addDefectListLine(dl1, "Износ подшипника", "Замена подшипника", "Подшипник 6208", bearing.getId(), 2, 1.5, 60_000);
        recalcTotals(dl1);
        defectListRepository.save(dl1);

        // Defect list #2 — overhaul of reactor Р-2 (corrosion)
        DefectList dl2 = new DefectList();
        dl2.setCode("DL-2026-0002");
        dl2.setTitle("Ведомость дефектов Р-2 (коррозия корпуса)");
        dl2.setEquipmentId(all.get(8).getId());
        dl2.setCreatedById(admin);
        dl2.setStatus(DefectListStatus.DRAFT);
        dl2.setNotes("Требуется согласование с инженером по надёжности");
        dl2 = defectListRepository.save(dl2);
        addDefectListLine(dl2, "Коррозия в нижней части корпуса", "Зачистка, сварка, повторные гидроиспытания", "Электроды, сварочные материалы", null, 1, 16.0, 320_000);
        addDefectListLine(dl2, "Замена уплотнительных колец фланцев", "Демонтаж, замена прокладок", "Прокладки DN100", null, 8, 4.0, 40_000);
        recalcTotals(dl2);
        defectListRepository.save(dl2);
    }

    private void addDefectListLine(DefectList parent, String description, String workScope,
                                   String materialSpec, UUID sparePartId, double qty,
                                   double hours, double cost) {
        DefectListLine line = new DefectListLine();
        line.setDefectList(parent);
        line.setDescription(description);
        line.setWorkScope(workScope);
        line.setMaterialSpecification(materialSpec);
        line.setSparePartId(sparePartId);
        line.setRequiredQuantity(qty);
        line.setEstimatedLaborHours(hours);
        line.setEstimatedCost(cost);
        parent.getLines().add(line);
        defectListLineRepository.save(line);
    }

    private void recalcTotals(DefectList d) {
        double hours = d.getLines().stream().mapToDouble(DefectListLine::getEstimatedLaborHours).sum();
        double cost = d.getLines().stream().mapToDouble(DefectListLine::getEstimatedCost).sum();
        d.setTotalLaborHours(hours);
        d.setTotalEstimatedCost(cost);
    }

    private void seedDictionaries() {
        seedCrit("CRIT-LOW", "Низкая", "Low", "Past", CriticalityLevel.LOW,
                1, 1, 0, 1, "Локальная остановка единицы оборудования без влияния на линию", 5);
        seedCrit("CRIT-MED", "Средняя", "Medium", "O'rta", CriticalityLevel.MEDIUM,
                2, 3, 1, 2, "Снижение производительности участка, требуется внеплановый ремонт", 3);
        seedCrit("CRIT-HIGH", "Высокая", "High", "Yuqori", CriticalityLevel.HIGH,
                4, 4, 3, 3, "Остановка технологической линии, риск срыва плана", 2);
        seedCrit("CRIT-CRIT", "Критическая", "Critical", "Juda yuqori", CriticalityLevel.CRITICAL,
                5, 5, 5, 4, "Авария с угрозой жизни/экологии, полная остановка производства", 1);

        seedDefectCategory("BEARING_WEAR", "Износ подшипника", "Bearing wear", "Podshipnik eskirishi");
        seedDefectCategory("SEAL_WEAR", "Износ уплотнения", "Seal wear", "Zichlagich eskirishi");
        seedDefectCategory("CORROSION", "Коррозия", "Corrosion", "Korroziya");
        seedDefectCategory("EROSION", "Эрозия", "Erosion", "Eroziya");
        seedDefectCategory("VIBRATION", "Аномальная вибрация", "Abnormal vibration", "Anomal tebranish");
        seedDefectCategory("LEAK", "Утечка", "Leak", "Oqish");
        seedDefectCategory("ELECTRICAL", "Электрическая неисправность", "Electrical fault", "Elektr nosozligi");

        seedSeverity("MINOR", "Незначительный", "Minor", "Kichik", 1);
        seedSeverity("MAJOR", "Значительный", "Major", "Katta", 5);
        seedSeverity("CRITICAL", "Критический", "Critical", "Juda katta", 10);

        seedFailureReason("MECH_WEAR", "Механический износ", "Mechanical wear", "Mexanik eskirish");
        seedFailureReason("OPERATION_ERROR", "Ошибка эксплуатации", "Operation error", "Ekspluatatsiya xatosi");
        seedFailureReason("OPERATING_MODE", "Нарушение режима", "Operating mode violation", "Rejim buzilishi");
        seedFailureReason("ELECTRICAL", "Электрическая неисправность", "Electrical fault", "Elektr nosozligi");
        seedFailureReason("INSTRUMENT_FAILURE", "Отказ КИПиА", "Instrumentation failure", "Asboblar ishdan chiqishi");
        seedFailureReason("EXTERNAL", "Внешнее воздействие", "External impact", "Tashqi ta'sir");
        seedFailureReason("POOR_REPAIR", "Некачественный ремонт", "Poor repair", "Sifatsiz ta'mirlash");
        seedFailureReason("INSUFFICIENT_LUB", "Отсутствие смазки", "Insufficient lubrication", "Moylash yetishmasligi");
        seedFailureReason("POOR_MATERIALS", "Некачественные материалы", "Poor materials", "Sifatsiz materiallar");
        seedFailureReason("CORROSION", "Коррозия", "Corrosion", "Korroziya");
        seedFailureReason("EROSION", "Эрозия", "Erosion", "Eroziya");

        seedRootCause("DESIGN_DEFECT", "Конструктивный дефект", "Design defect", "Konstruksiya nuqsoni");
        seedRootCause("MATERIAL_DEFECT", "Дефект материала", "Material defect", "Material nuqsoni");
        seedRootCause("MAINTENANCE_ERROR", "Ошибка обслуживания", "Maintenance error", "TX xatosi");
        seedRootCause("OPERATING_MODE", "Нарушение режима работы", "Operating mode violation", "Ishlash rejimi buzilishi");
        seedRootCause("INSUFFICIENT_LUB", "Недостаточная смазка", "Insufficient lubrication", "Yetarli moylash yo'q");
        seedRootCause("FATIGUE", "Усталость металла", "Metal fatigue", "Metall charchoqligi");

        seedCostCategory("LABOR", "Трудозатраты", "Labor", "Mehnat");
        seedCostCategory("MATERIALS", "Материалы и запчасти", "Materials and spares", "Materiallar");
        seedCostCategory("CTR", "Подрядные работы", "Contracted works", "Pudrat ishlari");
        seedCostCategory("DOWNTIME", "Потери от простоя", "Downtime losses", "Toxtalish yo'qotishlari");
    }

    private void seedCrit(String code, String name, String nameEn, String nameUz, CriticalityLevel level,
                          int safety, int production, int ecological, int energy,
                          String failureConsequence, int repairPriority) {
        CriticalityClass c = new CriticalityClass();
        c.setCode(code); c.setName(name); c.setNameEn(nameEn); c.setNameUz(nameUz); c.setLevel(level);
        c.setSafetyImpact(safety);
        c.setProductionImpact(production);
        c.setEcologicalImpact(ecological);
        c.setEnergyImpact(energy);
        c.setFailureConsequence(failureConsequence);
        c.setRepairPriority(repairPriority);
        criticalityClassRepository.save(c);
    }

    private void seedDefectCategory(String code, String name, String nameEn, String nameUz) {
        DefectCategory c = new DefectCategory();
        c.setCode(code); c.setName(name); c.setNameEn(nameEn); c.setNameUz(nameUz);
        defectCategoryRepository.save(c);
    }

    private void seedSeverity(String code, String name, String nameEn, String nameUz, int weight) {
        DefectSeverity s = new DefectSeverity();
        s.setCode(code); s.setName(name); s.setNameEn(nameEn); s.setNameUz(nameUz); s.setWeight(weight);
        defectSeverityRepository.save(s);
    }

    private void seedFailureReason(String code, String name, String nameEn, String nameUz) {
        FailureReason r = new FailureReason();
        r.setCode(code); r.setName(name); r.setNameEn(nameEn); r.setNameUz(nameUz);
        failureReasonRepository.save(r);
    }

    private void seedRootCause(String code, String name, String nameEn, String nameUz) {
        RootCause r = new RootCause();
        r.setCode(code); r.setName(name); r.setNameEn(nameEn); r.setNameUz(nameUz);
        rootCauseRepository.save(r);
    }

    private void seedCostCategory(String code, String name, String nameEn, String nameUz) {
        CostCategory c = new CostCategory();
        c.setCode(code); c.setName(name); c.setNameEn(nameEn); c.setNameUz(nameUz);
        costCategoryRepository.save(c);
    }

    private void seedDowntimeEvents(List<Equipment> all, Department plant, Department ammonia,
                                    Department urea, Department nitric) {
        Instant now = Instant.now();
        // recent downtimes for dashboard "latestDowntimes"
        seedDowntime(all.get(4), ammonia, now.minus(2, ChronoUnit.DAYS),
                now.minus(2, ChronoUnit.DAYS).plus(6, ChronoUnit.HOURS),
                DowntimeType.UNPLANNED, "Утечка сальника Н-103 — остановка на замену");
        seedDowntime(all.get(0), ammonia, now.minus(5, ChronoUnit.DAYS),
                now.minus(5, ChronoUnit.DAYS).plus(4, ChronoUnit.HOURS),
                DowntimeType.UNPLANNED, "Аварийная вибрация К-1");
        seedDowntime(all.get(13), nitric, now.minus(7, ChronoUnit.DAYS),
                now.minus(7, ChronoUnit.DAYS).plus(12, ChronoUnit.HOURS),
                DowntimeType.UNPLANNED, "Перегрев подшипника К-3");
        seedDowntime(all.get(8), urea, now.minus(10, ChronoUnit.DAYS),
                now.minus(10, ChronoUnit.DAYS).plus(48, ChronoUnit.HOURS),
                DowntimeType.UNPLANNED, "Капитальный ремонт реактора карбамида");
        seedDowntime(all.get(15), nitric, now.minus(15, ChronoUnit.DAYS),
                now.minus(15, ChronoUnit.DAYS).plus(18, ChronoUnit.HOURS),
                DowntimeType.UNPLANNED, "Аварийный останов Р-3");
        seedDowntime(all.get(2), ammonia, now.minus(20, ChronoUnit.DAYS),
                now.minus(20, ChronoUnit.DAYS).plus(36, ChronoUnit.HOURS),
                DowntimeType.PLANNED, "Плановое ТО Н-101");
        seedDowntime(all.get(6), ammonia, now.minus(25, ChronoUnit.DAYS),
                now.minus(25, ChronoUnit.DAYS).plus(5, ChronoUnit.HOURS),
                DowntimeType.PLANNED, "Чистка теплообменника Т-101");
        seedDowntime(all.get(9), urea, now.minus(3, ChronoUnit.DAYS),
                now.minus(3, ChronoUnit.DAYS).plus(2, ChronoUnit.HOURS),
                DowntimeType.UNPLANNED, "Шум в Н-201");
    }

    private void seedDowntime(Equipment equipment, Department department, Instant start, Instant end,
                              DowntimeType type, String description) {
        DowntimeEvent e = new DowntimeEvent();
        e.setEquipmentId(equipment.getId());
        e.setDepartmentId(department.getId());
        e.setStartAt(start);
        e.setEndAt(end);
        e.setDurationMinutes((int) java.time.Duration.between(start, end).toMinutes());
        e.setType(type);
        e.setDescription(description);
        downtimeEventRepository.save(e);
    }

    private void seedReliabilityMetrics(List<Equipment> all) {
        Random rnd = new Random(42);
        LocalDate today = LocalDate.now();
        for (int i = 0; i < Math.min(10, all.size()); i++) {
            Equipment eq = all.get(i);
            ReliabilityMetric m = new ReliabilityMetric();
            m.setEquipmentId(eq.getId());
            m.setMetricDate(today);
            m.setMtbfHours(2000.0 + rnd.nextInt(3000));
            m.setMttrHours(4.0 + rnd.nextInt(12));
            m.setAvailability(0.85 + rnd.nextDouble() * 0.14);
            m.setFailureRate(rnd.nextDouble() * 0.05);
            reliabilityMetricRepository.save(m);
        }
    }

    private void seedBudgetsAndCosts(Department plant, Department ammonia, Department urea, Department nitric) {
        CostCategory labor = costCategoryRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc().stream()
                .filter(c -> "LABOR".equals(c.getCode())).findFirst().orElseThrow();
        CostCategory materials = costCategoryRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc().stream()
                .filter(c -> "MATERIALS".equals(c.getCode())).findFirst().orElseThrow();
        CostCategory contractors = costCategoryRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc().stream()
                .filter(c -> "CTR".equals(c.getCode())).findFirst().orElseThrow();

        MaintenanceBudget plantBudget = new MaintenanceBudget();
        plantBudget.setYear(LocalDate.now().getYear());
        plantBudget.setDepartmentId(plant.getId());
        plantBudget.setStatus(BudgetStatus.APPROVED);
        plantBudget.setTotalPlanned(5_000_000);
        plantBudget.setTotalActual(3_200_000);
        plantBudget = budgetRepository.save(plantBudget);

        seedBudgetLine(plantBudget, labor.getId(), "Оплата труда ремонтников", 1_500_000, 980_000);
        seedBudgetLine(plantBudget, materials.getId(), "Запчасти и материалы", 2_500_000, 1_620_000);
        seedBudgetLine(plantBudget, contractors.getId(), "Подрядные работы", 1_000_000, 600_000);

        for (Department d : List.of(ammonia, urea, nitric)) {
            MaintenanceBudget b = new MaintenanceBudget();
            b.setYear(LocalDate.now().getYear());
            b.setDepartmentId(d.getId());
            b.setStatus(BudgetStatus.APPROVED);
            b.setTotalPlanned(1_500_000);
            b.setTotalActual(950_000 + (long) (Math.random() * 400_000));
            b = budgetRepository.save(b);
            seedBudgetLine(b, labor.getId(), "Оплата труда " + d.getCode(), 500_000, 320_000);
            seedBudgetLine(b, materials.getId(), "Материалы " + d.getCode(), 800_000, 550_000);
            seedBudgetLine(b, contractors.getId(), "Подрядчики " + d.getCode(), 200_000, 120_000);
        }

        Instant now = Instant.now();
        for (int i = 0; i < 8; i++) {
            ActualCost ac = new ActualCost();
            ac.setCostCategoryId(i % 2 == 0 ? materials.getId() : labor.getId());
            ac.setAmount(50_000 + (long) (Math.random() * 200_000));
            ac.setCostDate(now.minus(i * 3L, ChronoUnit.DAYS));
            ac.setNotes("Фактические затраты по ремонту #" + (i + 1));
            ac.setStatus(i < 5 ? ActualCostStatus.APPROVED : ActualCostStatus.PENDING);
            if (i < 5) {
                ac.setReviewedAt(now.minus(i * 3L, ChronoUnit.DAYS).plus(1, ChronoUnit.DAYS));
                ac.setReviewComment("Утверждено автоматически в демо-режиме");
            }
            ac = actualCostRepository.save(ac);
            if (i < 5) {
                seedActualCostReviewEvent(
                        ac,
                        "REVIEW",
                        "APPROVED",
                        "Actual cost approved",
                        ac.getReviewComment(),
                        null,
                        ac.getStatus().name(),
                        ac.getReviewedAt() != null ? ac.getReviewedAt() : ac.getCostDate()
                );
            } else {
                seedActualCostReviewEvent(
                        ac,
                        "SLA",
                        i == 5 ? "OVERDUE" : "DUE_SOON",
                        i == 5 ? "Actual cost review overdue" : "Actual cost review due soon",
                        ac.getNotes(),
                        i == 5 ? NotificationSeverity.WARNING.name() : NotificationSeverity.INFO.name(),
                        ac.getStatus().name(),
                        ac.getCostDate()
                );
                if (i == 5) {
                    seedActualCostHandoverEvent(ac);
                }
            }
        }
    }

    private void seedActualCostReviewEvent(ActualCost actualCost, String eventGroup, String eventCode,
                                           String title, String description, String severity, String status,
                                           Instant occurredAt) {
        ActualCostReviewEvent event = new ActualCostReviewEvent();
        event.setActualCostId(actualCost.getId());
        event.setActorUserId(actualCost.getReviewedById());
        event.setSource("SYSTEM");
        event.setEventGroup(eventGroup);
        event.setEventCode(eventCode);
        event.setTitle(title);
        event.setDescription(description);
        event.setSeverity(severity);
        event.setStatus(status);
        event.setOccurredAt(occurredAt != null ? occurredAt : Instant.now());
        actualCostReviewEventRepository.save(event);
    }

    private void seedActualCostHandoverEvent(ActualCost actualCost) {
        ActualCostReviewEvent event = new ActualCostReviewEvent();
        event.setActualCostId(actualCost.getId());
        event.setSource("SYSTEM");
        event.setEventGroup("ROUTE");
        event.setEventCode("HANDOVER");
        event.setTitle("Actual cost review handed over");
        event.setDescription("Demo SLA handover to chief accountant");
        event.setStatus(actualCost.getStatus().name());
        event.setPreviousApprovalRoleCode("FINANCE_MANAGER");
        event.setNextApprovalRoleCode("CHIEF_ACCOUNTANT");
        event.setPreviousThresholdHours(24);
        event.setNextThresholdHours(12);
        event.setHandoverComment("Demo overdue financial review reassignment");
        event.setAcknowledgementComment("Demo acknowledgement");
        event.setOccurredAt(actualCost.getCostDate().plus(1, ChronoUnit.DAYS));
        actualCostReviewEventRepository.save(event);
    }

    private void seedBudgetLine(MaintenanceBudget budget, UUID categoryId, String description,
                                double planned, double actual) {
        BudgetLine line = new BudgetLine();
        line.setBudget(budget);
        line.setCostCategoryId(categoryId);
        line.setDescription(description);
        line.setPlannedAmount(planned);
        line.setActualAmount(actual);
        budgetLineRepository.save(line);
    }

    private void seedStockMovements(Warehouse mainWh, Warehouse ammWh, SparePart bearing,
                                    SparePart seal, SparePart gasket, SparePart oilFilter, UUID admin) {
        Instant now = Instant.now();
        seedMovement(mainWh.getId(), bearing.getId(), StockMovementType.RECEIPT, 20,
                now.minus(15, ChronoUnit.DAYS), "Поступление от поставщика", admin);
        seedMovement(mainWh.getId(), seal.getId(), StockMovementType.ISSUE, 4,
                now.minus(10, ChronoUnit.DAYS), "Выдача на ремонт Н-103", admin);
        seedMovement(mainWh.getId(), gasket.getId(), StockMovementType.ISSUE, 12,
                now.minus(7, ChronoUnit.DAYS), "Выдача на ремонт Н-201", admin);
        seedMovement(mainWh.getId(), oilFilter.getId(), StockMovementType.ISSUE, 6,
                now.minus(5, ChronoUnit.DAYS), "Выдача на ТО компрессоров", admin);
        seedMovement(ammWh.getId(), bearing.getId(), StockMovementType.ISSUE, 2,
                now.minus(3, ChronoUnit.DAYS), "Выдача на замену подшипника К-3", admin);
        seedMovement(mainWh.getId(), seal.getId(), StockMovementType.ISSUE, 2,
                now.minus(1, ChronoUnit.DAYS), "Выдача на ремонт Р-2", admin);
    }

    private void seedMovement(UUID warehouseId, UUID sparePartId, StockMovementType type, double qty,
                              Instant occurredAt, String notes, UUID createdById) {
        StockMovement m = new StockMovement();
        m.setWarehouseId(warehouseId);
        m.setSparePartId(sparePartId);
        m.setType(type);
        m.setQuantity(qty);
        m.setOccurredAt(occurredAt);
        m.setNotes(notes);
        m.setCreatedById(createdById);
        stockMovementRepository.save(m);
    }

    private void seedNotifications(UUID admin) {
        if (admin == null) return;
        seedNotification(admin, "Критическая заявка RR-2026-0002", "Вибрация компрессора К-1 — назначить исполнителя",
                NotificationSeverity.CRITICAL, "RepairRequest");
        seedNotification(admin, "Заявка закрыта RR-2026-0001", "Течь Н-103 устранена",
                NotificationSeverity.INFO, "RepairRequest");
        seedNotification(admin, "Просроченный ППР-task", "Ревизия клапана К-201 просрочена на 3 дня",
                NotificationSeverity.WARNING, "PprTask");
        seedNotification(admin, "Низкий остаток запчастей", "SP-SEAL-AMM1: 8 шт (мин 10)",
                NotificationSeverity.WARNING, "WarehouseStock");
        List<ActualCost> pendingActualCosts = actualCostRepository
                .findAllByStatusAndIsDeletedFalseOrderByUpdatedAtDesc(ActualCostStatus.PENDING).stream()
                .limit(3)
                .toList();
        for (ActualCost actualCost : pendingActualCosts) {
            seedNotification(
                    admin,
                    "ActualCost на согласовании",
                    "Фактические затраты " + actualCost.getId() + " ждут финансового согласования",
                    NotificationSeverity.WARNING,
                    "ActualCost",
                    actualCost.getId().toString()
            );
        }
    }

    private void seedNotification(UUID recipient, String title, String message,
                                  NotificationSeverity severity, String entityType) {
        seedNotification(recipient, title, message, severity, entityType, null);
    }

    private void seedNotification(UUID recipient, String title, String message,
                                  NotificationSeverity severity, String entityType, String entityId) {
        Notification n = new Notification();
        n.setRecipientId(recipient);
        n.setTitle(title);
        n.setMessage(message);
        n.setChannel(NotificationChannel.WEB);
        n.setStatus(NotificationStatus.SENT);
        n.setSeverity(severity);
        n.setEntityType(entityType);
        n.setEntityId(entityId);
        notificationRepository.save(n);
    }

    private Department saveDept(String code, String name, String nameEn, String nameUz,
                                DepartmentType type, UUID parentId) {
        Department d = new Department();
        d.setCode(code);
        d.setName(name);
        d.setNameEn(nameEn);
        d.setNameUz(nameUz);
        d.setType(type);
        d.setParentId(parentId);
        return departmentRepository.save(d);
    }

    private EquipmentType saveType(String code, String name, String nameEn, String nameUz, String category) {
        EquipmentType t = new EquipmentType();
        t.setCode(code);
        t.setName(name);
        t.setNameEn(nameEn);
        t.setNameUz(nameUz);
        t.setCategory(category);
        return equipmentTypeRepository.save(t);
    }

    private Equipment saveEquipment(String code, String name, String inv, EquipmentType type, Department dept, EquipmentStatus status) {
        Equipment e = new Equipment();
        e.setCode(code);
        e.setName(name);
        e.setInventoryNumber(inv);
        e.setEquipmentTypeId(type.getId());
        e.setDepartmentId(dept.getId());
        e.setStatus(status);
        return equipmentRepository.save(e);
    }

    private void saveRegulation(String code, String name, EquipmentType type, MaintenanceKind kind, double hours,
                                PeriodicityUnit unit, int value) {
        MaintenanceRegulation r = new MaintenanceRegulation();
        r.setCode(code);
        r.setName(name);
        r.setEquipmentTypeId(type.getId());
        r.setMaintenanceKind(kind);
        r.setNormativeLaborHours(hours);
        r.setPeriodicityUnit(unit);
        r.setPeriodicityValue(value);
        regulationRepository.save(r);
    }

    private SparePart saveSparePart(String code, String name, String unit, double minStock) {
        SparePart p = new SparePart();
        p.setCode(code); p.setName(name); p.setUnit(unit);
        p.setMinStock(minStock); p.setKind(InventoryItemKind.SPARE_PART);
        p.setType(sparePartTypeRepository.findByCodeIgnoreCaseAndActiveTrue("OTHER").orElseThrow());
        p.setLegacyType("OTHER");
        return sparePartRepository.save(p);
    }

    private void seedStock(UUID warehouseId, UUID sparePartId, double qty, double minQty) {
        UUID sourceId = UUID.nameUUIDFromBytes(
                ("sample-data-opening-stock:" + warehouseId + ":" + sparePartId).getBytes(StandardCharsets.UTF_8));
        toirStockService.postIncrease(new StockReceiptCommand(
                warehouseId,
                sparePartId,
                null,
                java.math.BigDecimal.valueOf(qty),
                null,
                null,
                null,
                null,
                "SAMPLE_DATA_SEED",
                sourceId,
                null,
                "Sample data opening stock",
                "sample-data-opening-stock:" + warehouseId + ":" + sparePartId
        ), StockLedgerMovementType.ADJUSTMENT_INC);
        legacyStockProjectionService.syncWithMinQty(warehouseId, sparePartId, minQty);
    }

    private void seedRequest(Equipment equipment, String number, String title, PriorityLevel priority,
                             CriticalityLevel criticality, UUID reporterId) {
        if (reporterId == null) return;
        RepairRequest r = new RepairRequest();
        r.setNumber(number);
        r.setTitle(title);
        r.setDescription(title);
        r.setEquipmentId(equipment.getId());
        r.setDepartmentId(equipment.getDepartmentId());
        r.setReporterId(reporterId);
        r.setPriority(priority);
        r.setCriticality(criticality);
        r.setSource(RequestSource.MANUAL);
        repairRequestRepository.save(r);
    }

    private void seedDefect(Equipment equipment, String code, String title, String category,
                            String severity, String failureReason, String rootCause) {
        Defect d = new Defect();
        d.setCode(code);
        d.setTitle(title);
        d.setDescription(title);
        d.setEquipmentId(equipment.getId());
        d.setCategory(category);
        d.setSeverity(severity);
        d.setFailureReason(failureReason);
        d.setRootCause(rootCause);
        defectRepository.save(d);
    }

    private void seedWorkOrder(String number, String title, Equipment equipment, WorkOrderType type,
                               PriorityLevel priority, WorkOrderStatus status, UUID createdById, int closedDaysAgo) {
        WorkOrder wo = new WorkOrder();
        wo.setNumber(number);
        wo.setTitle(title);
        wo.setEquipmentId(equipment.getId());
        wo.setDepartmentId(equipment.getDepartmentId());
        wo.setType(type);
        wo.setPriority(priority);
        wo.setStatus(status);
        wo.setCreatedById(createdById);
        Instant now = Instant.now();
        if (status == WorkOrderStatus.IN_PROGRESS) {
            wo.setStartedAt(now.minus(2, ChronoUnit.DAYS));
        } else if (status == WorkOrderStatus.CLOSED) {
            Instant closedAt = now.minus(closedDaysAgo, ChronoUnit.DAYS);
            wo.setStartedAt(closedAt.minus(1, ChronoUnit.DAYS));
            wo.setCompletedAt(closedAt);
            wo.setResult("Работа выполнена в полном объёме");
            wo.setSummary("Сид: автоматически закрытый наряд");
            wo.setApprovedById(createdById);
        } else if (status == WorkOrderStatus.APPROVED) {
            wo.setApprovedById(createdById);
        }
        workOrderRepository.save(wo);
    }
}
