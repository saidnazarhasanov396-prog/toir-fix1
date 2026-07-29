package com.toir.service.pprcalendar;

import com.toir.enums.MaintenanceKind;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MaintenanceKindDisplayResolverTest {

    private final MaintenanceKindDisplayResolver resolver = new MaintenanceKindDisplayResolver();

    @Test
    void resolvesEveryMaintenanceKindToTheApprovedStableDisplay() {
        Map<MaintenanceKind, ExpectedDisplay> expected = Map.ofEntries(
                Map.entry(MaintenanceKind.PREVENTIVE,
                        new ExpectedDisplay("ТО", "Техническое обслуживание")),
                Map.entry(MaintenanceKind.PREDICTIVE,
                        new ExpectedDisplay("ПР", "Предиктивное обслуживание")),
                Map.entry(MaintenanceKind.CONDITION_BASED,
                        new ExpectedDisplay("ТС", "Обслуживание по состоянию")),
                Map.entry(MaintenanceKind.INSPECTION,
                        new ExpectedDisplay("ОС", "Осмотр")),
                Map.entry(MaintenanceKind.DIAGNOSTIC,
                        new ExpectedDisplay("ДГ", "Диагностика")),
                Map.entry(MaintenanceKind.CURRENT_REPAIR,
                        new ExpectedDisplay("ТР", "Текущий ремонт")),
                Map.entry(MaintenanceKind.MEDIUM_REPAIR,
                        new ExpectedDisplay("СР", "Средний ремонт")),
                Map.entry(MaintenanceKind.OVERHAUL,
                        new ExpectedDisplay("КР", "Капитальный ремонт")),
                Map.entry(MaintenanceKind.SEASONAL,
                        new ExpectedDisplay("СЗ", "Сезонные работы")),
                Map.entry(MaintenanceKind.METROLOGICAL,
                        new ExpectedDisplay("МТ", "Метрологические работы")),
                Map.entry(MaintenanceKind.ELECTRICAL,
                        new ExpectedDisplay("ЭЛ", "Электротехнические работы")),
                Map.entry(MaintenanceKind.INSTRUMENTATION,
                        new ExpectedDisplay("КИП", "Работы КИП"))
        );

        assertThat(expected).hasSize(MaintenanceKind.values().length);
        expected.forEach((kind, expectedDisplay) -> {
            MaintenanceKindDisplay display = resolver.resolve(kind);

            assertThat(display.maintenanceKind()).isEqualTo(kind);
            assertThat(display.displayCode()).isEqualTo(expectedDisplay.code());
            assertThat(display.displayName()).isEqualTo(expectedDisplay.name());
        });
    }

    @Test
    void returnsAnExplicitFallbackForAnUnresolvedMaintenanceKind() {
        assertThat(resolver.resolve(null)).isEqualTo(
                new MaintenanceKindDisplay(null, "—", "Вид обслуживания не определён"));
    }

    private record ExpectedDisplay(String code, String name) {
    }
}
