package com.toir.service.maintanance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.toir.dto.maintenanceschedule.MaintenanceScheduleCalculationRequest;
import com.toir.dto.maintenanceschedule.MaintenanceSchedulePreviewResponse;
import com.toir.dto.maintenanceschedule.MaintenanceSchedulePreviewSummary;
import com.toir.dto.pprplanning.PprPlanDto;
import com.toir.enums.MaintenanceScheduleAnchorMode;
import com.toir.enums.MaintenanceScheduleScopeType;
import com.toir.enums.PprPlanOrigin;
import com.toir.exception.RestException;
import com.toir.repository.ApprovalRequestRepository;
import com.toir.repository.PprPlanRepository;
import com.toir.repository.MaintenanceScheduleCalculationRepository;
import com.toir.service.PprPlanService;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MaintenanceScheduleCalculationServiceTest {

    @Mock MaintenanceScheduleService scheduleService;
    @Mock PprPlanService pprPlanService;
    @Mock PprPlanRepository planRepository;
    @Mock MaintenanceScheduleCalculationRepository calculationRepository;
    @Mock ApprovalRequestRepository approvalRequestRepository;

    MaintenanceScheduleCalculationService service;

    @BeforeEach
    void setUp() {
        service = new MaintenanceScheduleCalculationService(
                scheduleService,
                pprPlanService,
                planRepository,
                calculationRepository,
                approvalRequestRepository
        );
    }

    @Test
    void createPersistsASeparatedBuilderPlanWithoutStartingApproval() {
        MaintenanceScheduleCalculationRequest request = request();
        PprPlanDto saved = org.mockito.Mockito.mock(PprPlanDto.class);
        when(scheduleService.preview(any())).thenReturn(new MaintenanceSchedulePreviewResponse(
                List.of(),
                new MaintenanceSchedulePreviewSummary(1, 4, 0, 0)
        ));
        when(pprPlanService.createScheduleCalculation(any())).thenReturn(saved);

        var result = service.create(request);

        assertThat(result.plan()).isSameAs(saved);
        assertThat(result.approvalStatus()).isNull();
        verify(pprPlanService).createScheduleCalculation(any());
        verify(approvalRequestRepository, never()).save(any());
    }

    @Test
    void createRejectsAnEmptyCalculationBeforePersistingAnything() {
        when(scheduleService.preview(any())).thenReturn(new MaintenanceSchedulePreviewResponse(
                List.of(),
                new MaintenanceSchedulePreviewSummary(0, 0, 0, 2)
        ));

        assertThatThrownBy(() -> service.create(request()))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("no occurrences");

        verify(pprPlanService, never()).createScheduleCalculation(any());
    }

    @Test
    void requestCreatesAPprPayloadMarkedByTheDedicatedServiceOrigin() {
        assertThat(request().toPprPlanRequest().anchorMode())
                .isEqualTo(MaintenanceScheduleAnchorMode.CURRENT);
        assertThat(PprPlanOrigin.MAINTENANCE_SCHEDULE.name())
                .isEqualTo("MAINTENANCE_SCHEDULE");
    }

    private MaintenanceScheduleCalculationRequest request() {
        return new MaintenanceScheduleCalculationRequest(
                "Вариант 2027",
                "Проверочный вариант",
                LocalDate.of(2027, 1, 1),
                LocalDate.of(2027, 12, 31),
                MaintenanceScheduleScopeType.EQUIPMENT,
                List.of(UUID.randomUUID()),
                List.of(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                MaintenanceScheduleAnchorMode.CURRENT
        );
    }
}
