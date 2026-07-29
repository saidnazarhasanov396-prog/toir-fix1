package com.toir.service.pprcalendar;

import com.toir.enums.MaintenanceKind;
import org.springframework.stereotype.Component;

@Component
public class MaintenanceKindDisplayResolver {

    public MaintenanceKindDisplay resolve(MaintenanceKind maintenanceKind) {
        if (maintenanceKind == null) {
            return new MaintenanceKindDisplay(
                    null, "—", "Вид обслуживания не определён");
        }
        return switch (maintenanceKind) {
            case PREVENTIVE -> display(maintenanceKind, "ТО", "Техническое обслуживание");
            case PREDICTIVE -> display(maintenanceKind, "ПР", "Предиктивное обслуживание");
            case CONDITION_BASED -> display(maintenanceKind, "ТС", "Обслуживание по состоянию");
            case INSPECTION -> display(maintenanceKind, "ОС", "Осмотр");
            case DIAGNOSTIC -> display(maintenanceKind, "ДГ", "Диагностика");
            case CURRENT_REPAIR -> display(maintenanceKind, "ТР", "Текущий ремонт");
            case MEDIUM_REPAIR -> display(maintenanceKind, "СР", "Средний ремонт");
            case OVERHAUL -> display(maintenanceKind, "КР", "Капитальный ремонт");
            case SEASONAL -> display(maintenanceKind, "СЗ", "Сезонные работы");
            case METROLOGICAL -> display(maintenanceKind, "МТ", "Метрологические работы");
            case ELECTRICAL -> display(maintenanceKind, "ЭЛ", "Электротехнические работы");
            case INSTRUMENTATION -> display(maintenanceKind, "КИП", "Работы КИП");
        };
    }

    private MaintenanceKindDisplay display(
            MaintenanceKind maintenanceKind,
            String displayCode,
            String displayName) {
        return new MaintenanceKindDisplay(maintenanceKind, displayCode, displayName);
    }
}
