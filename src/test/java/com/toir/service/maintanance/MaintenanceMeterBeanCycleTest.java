package com.toir.service.maintanance;

import com.toir.config.NightlyMaintenanceJob;
import com.toir.service.MeterService;
import com.toir.service.WorkOrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ConfigurableApplicationContext;

import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class MaintenanceMeterBeanCycleTest {

    private static final Set<Class<?>> REAL_BEANS = Set.of(
            MeterService.class,
            WorkOrderService.class,
            MaintenanceAutomationService.class,
            NightlyMaintenanceJob.class
    );

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withInitializer(MaintenanceMeterBeanCycleTest::registerMockDependencies)
            .withBean(MeterService.class)
            .withBean(WorkOrderService.class)
            .withBean(MaintenanceAutomationService.class)
            .withBean(NightlyMaintenanceJob.class);

    @Test
    void maintenanceMeterWorkOrderServicesStartWithoutCircularDependency() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(MeterService.class);
            assertThat(context).hasSingleBean(WorkOrderService.class);
            assertThat(context).hasSingleBean(MaintenanceAutomationService.class);
            assertThat(context).hasSingleBean(NightlyMaintenanceJob.class);
        });
    }

    private static void registerMockDependencies(ConfigurableApplicationContext context) {
        ConfigurableListableBeanFactory beanFactory = context.getBeanFactory();
        for (Class<?> dependencyType : constructorDependencyTypes()) {
            if (REAL_BEANS.contains(dependencyType) || ObjectProvider.class.equals(dependencyType)) {
                continue;
            }
            if (beanFactory.getBeanNamesForType(dependencyType).length == 0) {
                beanFactory.registerSingleton(dependencyType.getName(), mock(dependencyType));
            }
        }
    }

    private static Set<Class<?>> constructorDependencyTypes() {
        return REAL_BEANS.stream()
                .flatMap(beanType -> Arrays.stream(primaryConstructor(beanType).getParameterTypes()))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static Constructor<?> primaryConstructor(Class<?> beanType) {
        Constructor<?>[] constructors = Arrays.stream(beanType.getDeclaredConstructors())
                .filter(constructor -> !constructor.isSynthetic())
                .toArray(Constructor<?>[]::new);
        assertThat(constructors)
                .as("Expected exactly one constructor for %s", beanType.getName())
                .hasSize(1);
        return constructors[0];
    }
}
