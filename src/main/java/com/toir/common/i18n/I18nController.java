package com.toir.common.i18n;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Ð¡ÐµÑ€Ð²ÐµÑ€Ð½Ñ‹Ðµ i18n-ÑÐ»Ð¾Ð²Ð°Ñ€Ð¸ Ð´Ð»Ñ enum-Ð·Ð½Ð°Ñ‡ÐµÐ½Ð¸Ð¹ Ð¸ ÐºÐ¾Ð´Ð¾Ð² ÑÑ‚Ð°Ñ‚ÑƒÑÐ¾Ð². Ð˜ÑÐ¿Ð¾Ð»ÑŒÐ·ÑƒÐµÑ‚ÑÑ
 * Ñ„Ñ€Ð¾Ð½Ñ‚ÐµÐ½Ð´Ð¾Ð¼, Ñ‡Ñ‚Ð¾Ð±Ñ‹ Ð½Ðµ Ð´ÑƒÐ±Ð»Ð¸Ñ€Ð¾Ð²Ð°Ñ‚ÑŒ Ð¿ÐµÑ€ÐµÐ²Ð¾Ð´ Ð² UI Ð¸ ÑÐµÑ€Ð²ÐµÑ€Ðµ. Ð¢Ñ€Ð¸ ÑÐ·Ñ‹ÐºÐ°: ru/en/uz.
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
                Map.entry("OPEN", "ÐžÑ‚ÐºÑ€Ñ‹Ñ‚Ð°"),
                Map.entry("REGISTERED", "Ð—Ð°Ñ€ÐµÐ³Ð¸ÑÑ‚Ñ€Ð¸Ñ€Ð¾Ð²Ð°Ð½Ð°"),
                Map.entry("NEEDS_CLARIFICATION", "Ð¢Ñ€ÐµÐ±ÑƒÐµÑ‚ ÑƒÑ‚Ð¾Ñ‡Ð½ÐµÐ½Ð¸Ñ"),
                Map.entry("ASSIGNED", "ÐÐ°Ð·Ð½Ð°Ñ‡ÐµÐ½Ð°"),
                Map.entry("IN_PROGRESS", "Ð’ Ñ€Ð°Ð±Ð¾Ñ‚Ðµ"),
                Map.entry("ON_HOLD", "ÐŸÑ€Ð¸Ð¾ÑÑ‚Ð°Ð½Ð¾Ð²Ð»ÐµÐ½Ð°"),
                Map.entry("REJECTED", "ÐžÑ‚ÐºÐ»Ð¾Ð½ÐµÐ½Ð°"),
                Map.entry("CLOSED", "Ð—Ð°ÐºÑ€Ñ‹Ñ‚Ð°"),
                Map.entry("CANCELLED", "ÐžÑ‚Ð¼ÐµÐ½ÐµÐ½Ð°"),
                Map.entry("OVERDUE", "ÐŸÑ€Ð¾ÑÑ€Ð¾Ñ‡ÐµÐ½Ð°")
        ));
        ru.put("priority", Map.of(
                "LOW", "ÐÐ¸Ð·ÐºÐ¸Ð¹",
                "MEDIUM", "Ð¡Ñ€ÐµÐ´Ð½Ð¸Ð¹",
                "HIGH", "Ð’Ñ‹ÑÐ¾ÐºÐ¸Ð¹",
                "EMERGENCY", "ÐÐ²Ð°Ñ€Ð¸Ð¹Ð½Ñ‹Ð¹"
        ));
        ru.put("criticality", Map.of(
                "LOW", "ÐÐ¸Ð·ÐºÐ°Ñ",
                "MEDIUM", "Ð¡Ñ€ÐµÐ´Ð½ÑÑ",
                "HIGH", "Ð’Ñ‹ÑÐ¾ÐºÐ°Ñ",
                "CRITICAL", "ÐšÑ€Ð¸Ñ‚Ð¸Ñ‡ÐµÑÐºÐ°Ñ"
        ));
        ru.put("workOrderStatus", Map.ofEntries(
                Map.entry("DRAFT", "Ð§ÐµÑ€Ð½Ð¾Ð²Ð¸Ðº"),
                Map.entry("PLANNED", "Ð—Ð°Ð¿Ð»Ð°Ð½Ð¸Ñ€Ð¾Ð²Ð°Ð½"),
                Map.entry("APPROVED", "Ð¡Ð¾Ð³Ð»Ð°ÑÐ¾Ð²Ð°Ð½"),
                Map.entry("IN_PROGRESS", "Ð’ Ñ€Ð°Ð±Ð¾Ñ‚Ðµ"),
                Map.entry("PAUSED", "ÐŸÑ€Ð¸Ð¾ÑÑ‚Ð°Ð½Ð¾Ð²Ð»ÐµÐ½"),
                Map.entry("COMPLETED", "Ð—Ð°Ð²ÐµÑ€ÑˆÑ‘Ð½"),
                Map.entry("CLOSED", "Ð—Ð°ÐºÑ€Ñ‹Ñ‚"),
                Map.entry("CANCELLED", "ÐžÑ‚Ð¼ÐµÐ½Ñ‘Ð½"),
                Map.entry("OVERDUE", "ÐŸÑ€Ð¾ÑÑ€Ð¾Ñ‡ÐµÐ½")
        ));
        ru.put("maintenanceKind", Map.ofEntries(
                Map.entry("PREVENTIVE", "ÐŸÑ€Ð¾Ñ„Ð¸Ð»Ð°ÐºÑ‚Ð¸ÐºÐ°"),
                Map.entry("PREDICTIVE", "ÐŸÑ€ÐµÐ´Ð¸ÐºÑ‚Ð¸Ð²Ð½Ð¾Ðµ"),
                Map.entry("CONDITION_BASED", "ÐŸÐ¾ ÑÐ¾ÑÑ‚Ð¾ÑÐ½Ð¸ÑŽ"),
                Map.entry("INSPECTION", "ÐžÑÐ¼Ð¾Ñ‚Ñ€"),
                Map.entry("DIAGNOSTIC", "Ð”Ð¸Ð°Ð³Ð½Ð¾ÑÑ‚Ð¸ÐºÐ°"),
                Map.entry("CURRENT_REPAIR", "Ð¢ÐµÐºÑƒÑ‰Ð¸Ð¹ Ñ€ÐµÐ¼Ð¾Ð½Ñ‚"),
                Map.entry("MEDIUM_REPAIR", "Ð¡Ñ€ÐµÐ´Ð½Ð¸Ð¹ Ñ€ÐµÐ¼Ð¾Ð½Ñ‚"),
                Map.entry("OVERHAUL", "ÐšÐ°Ð¿Ð¸Ñ‚Ð°Ð»ÑŒÐ½Ñ‹Ð¹ Ñ€ÐµÐ¼Ð¾Ð½Ñ‚"),
                Map.entry("SEASONAL", "Ð¡ÐµÐ·Ð¾Ð½Ð½Ð¾Ðµ Ð¢Ðž"),
                Map.entry("METROLOGICAL", "ÐœÐµÑ‚Ñ€Ð¾Ð»Ð¾Ð³Ð¸Ñ‡ÐµÑÐºÐ°Ñ Ð¿Ð¾Ð²ÐµÑ€ÐºÐ°"),
                Map.entry("ELECTRICAL", "Ð­Ð»ÐµÐºÑ‚Ñ€Ð¾Ñ‚ÐµÑ…Ð½Ð¸Ñ‡ÐµÑÐºÐ¾Ðµ Ð¢Ðž"),
                Map.entry("INSTRUMENTATION", "ÐšÐ˜ÐŸÐ¸Ð")
        ));
        ru.put("severity", Map.of(
                "OK", "ÐÐ¾Ñ€Ð¼Ð°",
                "WARN", "ÐŸÑ€ÐµÐ´ÑƒÐ¿Ñ€ÐµÐ¶Ð´ÐµÐ½Ð¸Ðµ",
                "ALARM", "ÐÐ²Ð°Ñ€Ð¸Ñ"
        ));
        ru.put("procurementStatus", Map.ofEntries(
                Map.entry("DRAFT", "Ð§ÐµÑ€Ð½Ð¾Ð²Ð¸Ðº"),
                Map.entry("SUBMITTED", "ÐžÑ‚Ð¿Ñ€Ð°Ð²Ð»ÐµÐ½Ð°"),
                Map.entry("APPROVED", "Ð¡Ð¾Ð³Ð»Ð°ÑÐ¾Ð²Ð°Ð½Ð°"),
                Map.entry("ORDERED", "Ð—Ð°ÐºÐ°Ð·Ð°Ð½Ð°"),
                Map.entry("RECEIVED", "ÐŸÐ¾Ð»ÑƒÑ‡ÐµÐ½Ð°"),
                Map.entry("CANCELLED", "ÐžÑ‚Ð¼ÐµÐ½ÐµÐ½Ð°"),
                Map.entry("REJECTED", "ÐžÑ‚ÐºÐ»Ð¾Ð½ÐµÐ½Ð°")
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
