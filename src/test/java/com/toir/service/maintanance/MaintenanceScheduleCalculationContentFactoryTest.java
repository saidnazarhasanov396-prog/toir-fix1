package com.toir.service.maintanance;

import static org.assertj.core.api.Assertions.assertThat;

import com.toir.entity.PprPlan;
import com.toir.enums.MaintenanceScheduleScopeType;
import java.lang.reflect.Modifier;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

class MaintenanceScheduleCalculationContentFactoryTest {

    @Test
    void isAnUnconditionalStatelessComponentRequiredByMaterializationService() {
        Class<MaintenanceScheduleCalculationContentFactory> factoryType =
                MaintenanceScheduleCalculationContentFactory.class;

        assertThat(factoryType.isAnnotationPresent(Component.class)).isTrue();
        assertThat(factoryType.isAnnotationPresent(Profile.class)).isFalse();
        assertThat(factoryType.isAnnotationPresent(Conditional.class)).isFalse();
        assertThat(Modifier.isAbstract(factoryType.getModifiers())).isFalse();
        assertThat(factoryType.getDeclaredFields()).isEmpty();
        assertThat(factoryType.getDeclaredConstructors()).hasSize(1);
        assertThat(factoryType.getDeclaredConstructors()[0].getParameterCount()).isZero();
        assertThat(Arrays.stream(MaintenanceScheduleMaterializationService.class
                        .getDeclaredConstructors())
                .anyMatch(constructor -> Arrays.asList(constructor.getParameterTypes())
                        .contains(factoryType)))
                .isTrue();
    }

    @Test
    void repeatedCallsProduceEqualContentWithoutRetainingRequestState() {
        PprPlan plan = new PprPlan();
        plan.setName("Annual maintenance");
        plan.setStartDate(LocalDate.of(2026, 1, 1));
        plan.setEndDate(LocalDate.of(2026, 12, 31));
        MaintenanceScheduleCalculationContentFactory factory =
                new MaintenanceScheduleCalculationContentFactory();

        MaintenanceScheduleCalculationContent first =
                factory.fromSnapshot(plan, 7L, List.of());
        MaintenanceScheduleCalculationContent second =
                factory.fromSnapshot(plan, 7L, List.of());

        assertThat(second).isEqualTo(first);
        assertThat(first.selectionScopeType())
                .isEqualTo(MaintenanceScheduleScopeType.EQUIPMENT_TYPE);
        assertThat(first.calculationRevision()).isEqualTo(7L);
        assertThat(first.snapshotItems()).isEmpty();
    }
}
