package com.toir.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Серверные i18n-словари для enum-значений и кодов статусов. Используется
 * фронтендом, чтобы не дублировать перевод в UI и сервере. Три языка: ru/en/uz.
 */
@RestController
@RequestMapping("/api/v1/i18n")
@Tag(name = "i18n")
public class I18nController {

    private static final Map<String, Map<String, Map<String, String>>> BUNDLES = build();

    @GetMapping("/{lang}")
    public Map<String, Map<String, String>> bundle(@PathVariable String lang) {
        return BUNDLES.getOrDefault(lang, BUNDLES.get("en"));
    }

    private static Map<String, Map<String, Map<String, String>>> build() {
        Map<String, Map<String, Map<String, String>>> m = new LinkedHashMap<>();

        // ---- Russian ----
        Map<String, Map<String, String>> ru = new LinkedHashMap<>();
        ru.put("requestStatus", Map.ofEntries(
                Map.entry("OPEN", "Открыта"),
                Map.entry("REGISTERED", "Зарегистрирована"),
                Map.entry("NEEDS_CLARIFICATION", "Требует уточнения"),
                Map.entry("ASSIGNED", "Назначена"),
                Map.entry("IN_PROGRESS", "В работе"),
                Map.entry("ON_HOLD", "Приостановлена"),
                Map.entry("REJECTED", "Отклонена"),
                Map.entry("CLOSED", "Закрыта"),
                Map.entry("CANCELLED", "Отменена"),
                Map.entry("OVERDUE", "Просрочена")
        ));
        ru.put("priority", Map.of(
                "LOW", "Низкий",
                "MEDIUM", "Средний",
                "HIGH", "Высокий",
                "EMERGENCY", "Аварийный"
        ));
        ru.put("criticality", Map.of(
                "LOW", "Низкая",
                "MEDIUM", "Средняя",
                "HIGH", "Высокая",
                "CRITICAL", "Критическая"
        ));
        ru.put("workOrderStatus", Map.ofEntries(
                Map.entry("DRAFT", "Черновик"),
                Map.entry("PLANNED", "Запланирован"),
                Map.entry("APPROVED", "Согласован"),
                Map.entry("IN_PROGRESS", "В работе"),
                Map.entry("PAUSED", "Приостановлен"),
                Map.entry("COMPLETED", "Завершён"),
                Map.entry("CLOSED", "Закрыт"),
                Map.entry("CANCELLED", "Отменён"),
                Map.entry("OVERDUE", "Просрочен")
        ));
        ru.put("maintenanceKind", Map.ofEntries(
                Map.entry("PREVENTIVE", "Профилактика"),
                Map.entry("PREDICTIVE", "Предиктивное"),
                Map.entry("CONDITION_BASED", "По состоянию"),
                Map.entry("INSPECTION", "Осмотр"),
                Map.entry("DIAGNOSTIC", "Диагностика"),
                Map.entry("CURRENT_REPAIR", "Текущий ремонт"),
                Map.entry("MEDIUM_REPAIR", "Средний ремонт"),
                Map.entry("OVERHAUL", "Капитальный ремонт"),
                Map.entry("SEASONAL", "Сезонное ТО"),
                Map.entry("METROLOGICAL", "Метрологическая поверка"),
                Map.entry("ELECTRICAL", "Электротехническое ТО"),
                Map.entry("INSTRUMENTATION", "КИПиА")
        ));
        ru.put("severity", Map.of(
                "OK", "Норма",
                "WARN", "Предупреждение",
                "ALARM", "Авария"
        ));
        ru.put("procurementStatus", Map.ofEntries(
                Map.entry("DRAFT", "Черновик"),
                Map.entry("SUBMITTED", "Отправлена"),
                Map.entry("APPROVED", "Согласована"),
                Map.entry("ORDERED", "Заказана"),
                Map.entry("RECEIVED", "Получена"),
                Map.entry("CANCELLED", "Отменена"),
                Map.entry("REJECTED", "Отклонена")
        ));
        m.put("ru", ru);

        // ---- English ----
        Map<String, Map<String, String>> en = new LinkedHashMap<>();
        en.put("requestStatus", Map.ofEntries(
                Map.entry("OPEN", "Open"),
                Map.entry("REGISTERED", "Registered"),
                Map.entry("NEEDS_CLARIFICATION", "Needs clarification"),
                Map.entry("ASSIGNED", "Assigned"),
                Map.entry("IN_PROGRESS", "In progress"),
                Map.entry("ON_HOLD", "On hold"),
                Map.entry("REJECTED", "Rejected"),
                Map.entry("CLOSED", "Closed"),
                Map.entry("CANCELLED", "Cancelled"),
                Map.entry("OVERDUE", "Overdue")
        ));
        en.put("priority", Map.of(
                "LOW", "Low",
                "MEDIUM", "Medium",
                "HIGH", "High",
                "EMERGENCY", "Emergency"
        ));
        en.put("criticality", Map.of(
                "LOW", "Low",
                "MEDIUM", "Medium",
                "HIGH", "High",
                "CRITICAL", "Critical"
        ));
        en.put("workOrderStatus", Map.ofEntries(
                Map.entry("DRAFT", "Draft"),
                Map.entry("PLANNED", "Planned"),
                Map.entry("APPROVED", "Approved"),
                Map.entry("IN_PROGRESS", "In progress"),
                Map.entry("PAUSED", "Paused"),
                Map.entry("COMPLETED", "Completed"),
                Map.entry("CLOSED", "Closed"),
                Map.entry("CANCELLED", "Cancelled"),
                Map.entry("OVERDUE", "Overdue")
        ));
        en.put("maintenanceKind", Map.ofEntries(
                Map.entry("PREVENTIVE", "Preventive"),
                Map.entry("PREDICTIVE", "Predictive"),
                Map.entry("CONDITION_BASED", "Condition-based"),
                Map.entry("INSPECTION", "Inspection"),
                Map.entry("DIAGNOSTIC", "Diagnostic"),
                Map.entry("CURRENT_REPAIR", "Current repair"),
                Map.entry("MEDIUM_REPAIR", "Medium repair"),
                Map.entry("OVERHAUL", "Overhaul"),
                Map.entry("SEASONAL", "Seasonal"),
                Map.entry("METROLOGICAL", "Metrological calibration"),
                Map.entry("ELECTRICAL", "Electrical maintenance"),
                Map.entry("INSTRUMENTATION", "Instrumentation")
        ));
        en.put("severity", Map.of(
                "OK", "OK",
                "WARN", "Warning",
                "ALARM", "Alarm"
        ));
        en.put("procurementStatus", Map.ofEntries(
                Map.entry("DRAFT", "Draft"),
                Map.entry("SUBMITTED", "Submitted"),
                Map.entry("APPROVED", "Approved"),
                Map.entry("ORDERED", "Ordered"),
                Map.entry("RECEIVED", "Received"),
                Map.entry("CANCELLED", "Cancelled"),
                Map.entry("REJECTED", "Rejected")
        ));
        m.put("en", en);

        // ---- Uzbek ----
        Map<String, Map<String, String>> uz = new LinkedHashMap<>();
        uz.put("requestStatus", Map.ofEntries(
                Map.entry("OPEN", "Ochiq"),
                Map.entry("REGISTERED", "Ro'yxatdan o'tgan"),
                Map.entry("NEEDS_CLARIFICATION", "Aniqlashtirish kerak"),
                Map.entry("ASSIGNED", "Tayinlangan"),
                Map.entry("IN_PROGRESS", "Jarayonda"),
                Map.entry("ON_HOLD", "To'xtatilgan"),
                Map.entry("REJECTED", "Rad etilgan"),
                Map.entry("CLOSED", "Yopilgan"),
                Map.entry("CANCELLED", "Bekor qilingan"),
                Map.entry("OVERDUE", "Muddati o'tgan")
        ));
        uz.put("priority", Map.of(
                "LOW", "Past",
                "MEDIUM", "O'rta",
                "HIGH", "Yuqori",
                "EMERGENCY", "Favqulodda"
        ));
        uz.put("criticality", Map.of(
                "LOW", "Past",
                "MEDIUM", "O'rta",
                "HIGH", "Yuqori",
                "CRITICAL", "Juda yuqori"
        ));
        uz.put("workOrderStatus", Map.ofEntries(
                Map.entry("DRAFT", "Qoralama"),
                Map.entry("PLANNED", "Rejalashtirilgan"),
                Map.entry("APPROVED", "Tasdiqlangan"),
                Map.entry("IN_PROGRESS", "Jarayonda"),
                Map.entry("PAUSED", "To'xtatilgan"),
                Map.entry("COMPLETED", "Tugallangan"),
                Map.entry("CLOSED", "Yopilgan"),
                Map.entry("CANCELLED", "Bekor qilingan"),
                Map.entry("OVERDUE", "Muddati o'tgan")
        ));
        uz.put("maintenanceKind", Map.ofEntries(
                Map.entry("PREVENTIVE", "Profilaktika"),
                Map.entry("PREDICTIVE", "Prediktiv"),
                Map.entry("CONDITION_BASED", "Holatga qarab"),
                Map.entry("INSPECTION", "Ko'rik"),
                Map.entry("DIAGNOSTIC", "Diagnostika"),
                Map.entry("CURRENT_REPAIR", "Joriy ta'mirlash"),
                Map.entry("MEDIUM_REPAIR", "O'rta ta'mirlash"),
                Map.entry("OVERHAUL", "Kapital ta'mirlash"),
                Map.entry("SEASONAL", "Mavsumiy TX"),
                Map.entry("METROLOGICAL", "Metrologik tekshiruv"),
                Map.entry("ELECTRICAL", "Elektr TX"),
                Map.entry("INSTRUMENTATION", "KIPiA")
        ));
        uz.put("severity", Map.of(
                "OK", "Normal",
                "WARN", "Ogohlantirish",
                "ALARM", "Favqulodda"
        ));
        uz.put("procurementStatus", Map.ofEntries(
                Map.entry("DRAFT", "Qoralama"),
                Map.entry("SUBMITTED", "Yuborilgan"),
                Map.entry("APPROVED", "Tasdiqlangan"),
                Map.entry("ORDERED", "Buyurtma berilgan"),
                Map.entry("RECEIVED", "Qabul qilingan"),
                Map.entry("CANCELLED", "Bekor qilingan"),
                Map.entry("REJECTED", "Rad etilgan")
        ));
        m.put("uz", uz);

        return m;
    }
}
