package com.toir.config;

import com.toir.entity.Brigade;
import com.toir.entity.BrigadeMember;
import com.toir.repository.BrigadeMemberRepository;
import com.toir.repository.BrigadeRepository;
import com.toir.entity.CalibrationRecord;
import com.toir.repository.CalibrationRecordRepository;
import com.toir.entity.CertificationType;
import com.toir.repository.CertificationTypeRepository;
import com.toir.entity.UserCertification;
import com.toir.repository.UserCertificationRepository;
import com.toir.enums.ConditionParameter;
import com.toir.entity.ConditionReading;
import com.toir.repository.ConditionReadingRepository;
import com.toir.repository.DepartmentRepository;
import com.toir.entity.Equipment;
import com.toir.repository.EquipmentRepository;
import com.toir.entity.EquipmentSparePart;
import com.toir.repository.EquipmentSparePartRepository;
import com.toir.entity.InspectionCheckpoint;
import com.toir.entity.InspectionRoute;
import com.toir.repository.InspectionRouteRepository;
import com.toir.entity.KnowledgeArticle;
import com.toir.repository.KnowledgeArticleRepository;
import com.toir.entity.SparePart;
import com.toir.repository.SparePartRepository;
import com.toir.entity.User;
import com.toir.repository.UserRepository;
import com.toir.entity.WebhookSubscription;
import com.toir.repository.WebhookSubscriptionRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Второй этап наполнения: бригады, сертификаты, поверки, показания
 * condition monitoring, маршруты обходов, M2M оборудование↔запчасть,
 * webhook-подписки. Запускается после {@link SampleDataSeeder}.
 */
@Component
@Order(30)
@Transactional
@Profile("dev")
@ConditionalOnProperty(name = "app.bootstrap.seed-demo-data", havingValue = "true")
public class ExtendedDataSeeder implements CommandLineRunner {

    private final KnowledgeArticleRepository knowledgeRepo;
    private final BrigadeRepository brigadeRepo;
    private final BrigadeMemberRepository brigadeMemberRepo;
    private final CertificationTypeRepository certTypeRepo;
    private final UserCertificationRepository userCertRepo;
    private final CalibrationRecordRepository calibRepo;
    private final ConditionReadingRepository conditionRepo;
    private final InspectionRouteRepository routeRepo;
    private final EquipmentSparePartRepository equipmentSparePartRepo;
    private final WebhookSubscriptionRepository webhookRepo;
    private final DepartmentRepository departmentRepository;
    private final UserRepository userRepository;
    private final EquipmentRepository equipmentRepository;
    private final SparePartRepository sparePartRepository;

    public ExtendedDataSeeder(BrigadeRepository brigadeRepo,
                              BrigadeMemberRepository brigadeMemberRepo,
                              CertificationTypeRepository certTypeRepo,
                              UserCertificationRepository userCertRepo,
                              CalibrationRecordRepository calibRepo,
                              ConditionReadingRepository conditionRepo,
                              InspectionRouteRepository routeRepo,
                              EquipmentSparePartRepository equipmentSparePartRepo,
                              WebhookSubscriptionRepository webhookRepo,
                              DepartmentRepository departmentRepository,
                              UserRepository userRepository,
                              EquipmentRepository equipmentRepository,
                              SparePartRepository sparePartRepository,
                              KnowledgeArticleRepository knowledgeRepo) {
        this.brigadeRepo = brigadeRepo;
        this.brigadeMemberRepo = brigadeMemberRepo;
        this.certTypeRepo = certTypeRepo;
        this.userCertRepo = userCertRepo;
        this.calibRepo = calibRepo;
        this.conditionRepo = conditionRepo;
        this.routeRepo = routeRepo;
        this.equipmentSparePartRepo = equipmentSparePartRepo;
        this.webhookRepo = webhookRepo;
        this.departmentRepository = departmentRepository;
        this.userRepository = userRepository;
        this.equipmentRepository = equipmentRepository;
        this.sparePartRepository = sparePartRepository;
        this.knowledgeRepo = knowledgeRepo;
    }

    @Override
    public void run(String... args) {
        if (brigadeRepo.countByIsDeletedFalse() > 0) return;

        seedCertificationTypes();
        seedBrigadesAndMembers();
        seedUserCertifications();
        seedCalibrationRecords();
        seedConditionReadings();
        seedInspectionRoutes();
        seedEquipmentSparePartsCatalog();
        seedWebhookSubscriptions();
        seedKnowledgeArticles();
    }

    private void seedKnowledgeArticles() {
        List<Equipment> eqs = equipmentRepository.findAllByIsDeletedFalse();
        Equipment compressor = eqs.stream()
                .filter(e -> e.getCode() != null && e.getCode().contains("CMP"))
                .findFirst()
                .orElse(null);
        Equipment reactor = eqs.stream()
                .filter(e -> e.getCode() != null && e.getCode().contains("RCT"))
                .findFirst()
                .orElse(null);

        KnowledgeArticle a1 = new KnowledgeArticle();
        a1.setCode("LL-2026-001");
        a1.setTitle("Износ подшипника компрессора синтез-газа — ранняя диагностика");
        a1.setKind("LESSON_LEARNED");
        a1.setEquipmentId(compressor != null ? compressor.getId() : null);
        a1.setProblem(
                "На компрессоре К-1 цеха аммиака при вибрации >4.5 mm/s произошёл отказ "
                        + "подшипника, внеплановый простой 16 часов, потери производительности.");
        a1.setRootCause(
                "Несвоевременное обнаружение роста уровня вибрации. Оператор не реагировал "
                        + "на плавный рост параметра в течение 3 смен.");
        a1.setSolution(
                "1. Установить автоматические пороги WARN=4.5 mm/s и ALARM=7.1 mm/s. "
                        + "2. При WARN — обязательный осмотр в течение текущей смены. "
                        + "3. При ALARM — немедленная остановка с созданием заявки HIGH.");
        a1.setPreventiveActions(
                "Еженедельная проверка состояния подшипников по ГОСТ ИСО 10816-3. "
                        + "Смазка по регламенту каждые 2000 часов наработки.");
        a1.setTags(List.of("vibration", "bearing", "compressor", "predictive"));
        knowledgeRepo.save(a1);

        KnowledgeArticle a2 = new KnowledgeArticle();
        a2.setCode("KB-001");
        a2.setTitle("Процедура поверки манометров КИПиА");
        a2.setKind("PROCEDURE");
        a2.setProblem("Типовая процедура метрологической поверки для приборов давления.");
        a2.setRootCause("Периодическая поверка обязательна по ФЗ-102 «Об обеспечении единства измерений».");
        a2.setSolution(
                "1. Подготовить эталонный манометр с актуальной поверкой. "
                        + "2. Провести сравнение при 0, 25, 50, 75, 100% шкалы. "
                        + "3. Зафиксировать максимальную погрешность. "
                        + "4. Оформить свидетельство о поверке в системе.");
        a2.setPreventiveActions("Контроль даты следующей поверки в разделе «Поверки СИ».");
        a2.setTags(List.of("metrology", "pressure", "procedure"));
        knowledgeRepo.save(a2);

        KnowledgeArticle a3 = new KnowledgeArticle();
        a3.setCode("TS-2026-001");
        a3.setTitle("Диагностика коррозии корпуса реактора");
        a3.setKind("TROUBLESHOOTING");
        a3.setEquipmentId(reactor != null ? reactor.getId() : null);
        a3.setProblem(
                "Признаки коррозии на корпусе реактора окисления аммиака. Риск разгерметизации.");
        a3.setRootCause(
                "Агрессивная среда (HNO3 + NH3) + локальные зоны с нарушением пассивного "
                        + "слоя нержавеющей стали.");
        a3.setSolution(
                "Толщинометрия УЗК в 12 точках контура. При утонении >15% от номинала — "
                        + "вывод в ремонт с заменой или наплавкой повреждённого участка.");
        a3.setPreventiveActions(
                "Плановая толщинометрия каждые 6 месяцев. Визуальный осмотр в рамках "
                        + "ежемесячного обхода.");
        a3.setTags(List.of("corrosion", "reactor", "ndt", "inspection"));
        knowledgeRepo.save(a3);
    }

    private void seedCertificationTypes() {
        List<CertificationType> types = List.of(
                ct("CERT-WELD-NAKS", "Аттестация сварщика НАКС", "NAKS welder", "NAKS payvandchi", "WELDING", 36),
                ct("CERT-ELECTRO-V", "Электробезопасность V группа", "Electrical safety V", "Elektr xavfsizligi V", "ELECTRICAL", 12),
                ct("CERT-HEIGHT", "Допуск к высотным работам", "Height works", "Balandlik ishlari", "HEIGHT", 12),
                ct("CERT-NDT", "Неразрушающий контроль", "NDT inspection", "Buzmasdan nazorat", "NDT", 36),
                ct("CERT-GAS-HAZ", "Работы в газоопасной среде", "Gas-hazard works", "Gaz xavfli ishlar", "SAFETY", 12)
        );
        certTypeRepo.saveAll(types);
    }

    private CertificationType ct(String code, String name, String nameEn, String nameUz, String cat, int months) {
        CertificationType t = new CertificationType();
        t.setCode(code);
        t.setName(name);
        t.setNameEn(nameEn);
        t.setNameUz(nameUz);
        t.setCategory(cat);
        t.setValidityMonths(months);
        return t;
    }

    private void seedBrigadesAndMembers() {
        List<User> users = userRepository.findAllByIsDeletedFalse();
        if (users.isEmpty()) return;
        var amm = departmentRepository.findAllByIsDeletedFalse().stream()
                .filter(d -> "NAV-AMM".equals(d.getCode())).findFirst().orElse(null);
        var urea = departmentRepository.findAllByIsDeletedFalse().stream()
                .filter(d -> "NAV-UREA".equals(d.getCode())).findFirst().orElse(null);

        Brigade b1 = new Brigade();
        b1.setCode("BR-AMM-MECH");
        b1.setName("Бригада механиков цеха аммиака");
        b1.setDepartmentId(amm != null ? amm.getId() : null);
        b1.setForemanId(users.get(0).getId());
        b1.setSpecialization("Механические ремонты роторного оборудования");
        brigadeRepo.save(b1);

        Brigade b2 = new Brigade();
        b2.setCode("BR-UREA-INS");
        b2.setName("Бригада КИПиА цеха карбамида");
        b2.setDepartmentId(urea != null ? urea.getId() : null);
        b2.setForemanId(users.get(0).getId());
        b2.setSpecialization("Приборы КИПиА, метрологическое обслуживание");
        brigadeRepo.save(b2);

        BrigadeMember fm1 = new BrigadeMember();
        fm1.setBrigade(b1);
        fm1.setUserId(users.get(0).getId());
        fm1.setRoleCode("FOREMAN");
        fm1.setGrade(6);
        fm1.setQualifications(List.of("WELDING", "MECHANICAL"));
        brigadeMemberRepo.save(fm1);
    }

    private void seedUserCertifications() {
        List<User> users = userRepository.findAllByIsDeletedFalse();
        if (users.isEmpty()) return;
        LocalDate today = LocalDate.now();

        UserCertification c1 = new UserCertification();
        c1.setUserId(users.get(0).getId());
        c1.setTypeCode("CERT-WELD-NAKS");
        c1.setCertificateNumber("НАКС-2025-00142");
        c1.setIssuedBy("НАКС");
        c1.setIssuedAt(today.minusMonths(6));
        c1.setExpiresAt(today.minusMonths(6).plusMonths(36));
        c1.setGradeOrLevel("II");
        c1.setStatus("ACTIVE");
        userCertRepo.save(c1);

        UserCertification c2 = new UserCertification();
        c2.setUserId(users.get(0).getId());
        c2.setTypeCode("CERT-ELECTRO-V");
        c2.setCertificateNumber("ЭБ-2025-0042");
        c2.setIssuedBy("Ростехнадзор");
        c2.setIssuedAt(today.minusMonths(11));
        c2.setExpiresAt(today.plusDays(20)); // скоро истекает → будет в expiring
        c2.setGradeOrLevel("V");
        c2.setStatus("ACTIVE");
        userCertRepo.save(c2);
    }

    private void seedCalibrationRecords() {
        List<Equipment> eqs = equipmentRepository.findAllByIsDeletedFalse();
        if (eqs.isEmpty()) return;

        // для первых трёх единиц оборудования — одна свежая поверка каждая
        for (int i = 0; i < Math.min(3, eqs.size()); i++) {
            Equipment eq = eqs.get(i);
            CalibrationRecord c = new CalibrationRecord();
            c.setEquipmentId(eq.getId());
            c.setCertificateNumber("POV-2026-" + String.format("%04d", i + 1));
            c.setPerformedBy("ЦСМ Навои");
            c.setPerformedAt(LocalDate.now().minusMonths(6 + i));
            c.setNextDueAt(LocalDate.now().plusDays(i == 0 ? -10 : 20 + i * 30)); // первая — просрочена
            c.setResult("PASS");
            c.setTolerance(0.5);
            c.setMeasuredError(0.15);
            c.setUnit("%");
            calibRepo.save(c);
        }
    }

    private void seedConditionReadings() {
        List<Equipment> eqs = equipmentRepository.findAllByIsDeletedFalse();
        if (eqs.isEmpty()) return;
        Instant now = Instant.now();

        // На первом оборудовании — тренд вибрации с одним ALARM
        Equipment eq = eqs.get(0);
        double[] values = {2.8, 3.1, 3.4, 3.9, 4.6, 5.2, 7.5};
        for (int i = 0; i < values.length; i++) {
            ConditionReading r = new ConditionReading();
            r.setEquipmentId(eq.getId());
            r.setParameter(ConditionParameter.VIBRATION);
            r.setValue(values[i]);
            r.setUnit("mm/s");
            r.setWarnHigh(4.5);
            r.setAlarmHigh(7.1);
            r.setRecordedAt(now.minus(values.length - i, ChronoUnit.HOURS));
            double v = values[i];
            r.setSeverity(v > 7.1 ? "ALARM" : v > 4.5 ? "WARN" : "OK");
            conditionRepo.save(r);
        }

        // На втором — температура с WARN
        if (eqs.size() >= 2) {
            Equipment eq2 = eqs.get(1);
            ConditionReading r = new ConditionReading();
            r.setEquipmentId(eq2.getId());
            r.setParameter(ConditionParameter.TEMPERATURE);
            r.setValue(85);
            r.setUnit("C");
            r.setWarnHigh(80.0);
            r.setAlarmHigh(100.0);
            r.setRecordedAt(now);
            r.setSeverity("WARN");
            conditionRepo.save(r);
        }
    }

    private void seedInspectionRoutes() {
        List<Equipment> eqs = equipmentRepository.findAllByIsDeletedFalse();
        if (eqs.isEmpty()) return;
        var amm = departmentRepository.findAllByIsDeletedFalse().stream()
                .filter(d -> "NAV-AMM".equals(d.getCode())).findFirst().orElse(null);

        InspectionRoute route = new InspectionRoute();
        route.setCode("IR-SHIFT-AMM");
        route.setName("Ежесменный обход цеха аммиака");
        route.setDepartmentId(amm != null ? amm.getId() : null);
        route.setFrequency("SHIFT");
        route.setTargetDurationMin(60);
        route.setDescription("Визуальный осмотр + измерение ключевых параметров");

        InspectionCheckpoint cp1 = new InspectionCheckpoint();
        cp1.setRoute(route);
        cp1.setOrderIndex(1);
        cp1.setEquipmentId(eqs.get(0).getId());
        cp1.setTitle("Проверка уровня масла компрессора");
        cp1.setCheckType("VISUAL");
        cp1.setMandatory(true);
        route.getCheckpoints().add(cp1);

        if (eqs.size() >= 2) {
            InspectionCheckpoint cp2 = new InspectionCheckpoint();
            cp2.setRoute(route);
            cp2.setOrderIndex(2);
            cp2.setEquipmentId(eqs.get(1).getId());
            cp2.setTitle("Замер температуры подшипника");
            cp2.setCheckType("MEASUREMENT");
            cp2.setExpectedMin(40.0);
            cp2.setExpectedMax(80.0);
            cp2.setExpectedUnit("C");
            cp2.setMandatory(true);
            route.getCheckpoints().add(cp2);
        }

        routeRepo.save(route);
    }

    private void seedEquipmentSparePartsCatalog() {
        List<Equipment> eqs = equipmentRepository.findAllByIsDeletedFalse();
        List<SparePart> parts = sparePartRepository.findAllByIsDeletedFalse();
        if (eqs.isEmpty() || parts.isEmpty()) return;

        // для первого оборудования — две применимые запчасти
        Equipment eq = eqs.get(0);
        for (int i = 0; i < Math.min(2, parts.size()); i++) {
            EquipmentSparePart esp = new EquipmentSparePart();
            esp.setEquipmentId(eq.getId());
            esp.setSparePartId(parts.get(i).getId());
            esp.setPosition(i == 0 ? "основной узел" : "вспомогательный узел");
            esp.setQuantityPerUnit(i == 0 ? 2.0 : 1.0);
            esp.setConsumptionRatePerYear(i == 0 ? 1.5 : 0.5);
            esp.setCriticality(i == 0 ? "CRITICAL" : "STANDARD");
            equipmentSparePartRepo.save(esp);
        }
    }

    private void seedWebhookSubscriptions() {
        WebhookSubscription sub = new WebhookSubscription();
        sub.setCode("SCADA-PLACEHOLDER");
        sub.setName("SCADA integration placeholder");
        sub.setTargetUrl("http://127.0.0.1:9999/scada/events"); // заглушка
        sub.setEvents(List.of("CONDITION_ALARM", "CONDITION_WARN", "DEFECT_CREATED"));
        sub.setActive(false); // выключена по умолчанию
        webhookRepo.save(sub);
    }
}
