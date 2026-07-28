package com.toir.service.maintanance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.toir.repository.ApprovalRequestRepository;
import com.toir.repository.MaintenanceScheduleCalculationLifecycleRepository;
import com.toir.repository.MaintenanceScheduleCalculationStatsProjection;
import com.toir.service.PprPlanService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MaintenanceScheduleCalculationStatsServiceTest {

    @Mock PprPlanService pprPlanService;
    @Mock MaintenanceScheduleCalculationLifecycleRepository calculationRepository;
    @Mock ApprovalRequestRepository approvalRequestRepository;

    MaintenanceScheduleCalculationDashboardService service;

    @BeforeEach
    void setUp() {
        service = new MaintenanceScheduleCalculationDashboardService(
                pprPlanService,
                calculationRepository,
                approvalRequestRepository
        );
    }

    @Test
    void statsReturnsBackendAggregatesForTheVisibleCalculationScope() {
        when(calculationRepository.stats(null, "pump", 2026))
                .thenReturn(new MaintenanceScheduleCalculationStatsProjection() {
                    @Override
                    public long getTotal() {
                        return 12;
                    }

                    @Override
                    public long getSaved() {
                        return 5;
                    }

                    @Override
                    public long getPendingApproval() {
                        return 3;
                    }

                    @Override
                    public long getApproved() {
                        return 4;
                    }
                });

        var result = service.stats(null, "  pump  ", 2026);

        assertThat(result.total()).isEqualTo(12);
        assertThat(result.saved()).isEqualTo(5);
        assertThat(result.pendingApproval()).isEqualTo(3);
        assertThat(result.approved()).isEqualTo(4);
    }
}
