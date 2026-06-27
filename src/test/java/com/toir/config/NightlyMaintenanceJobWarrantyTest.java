package com.toir.config;

import com.toir.service.CertificationService;
import com.toir.service.OperationalIssueScannerService;
import com.toir.service.OverdueDetectorService;
import com.toir.service.equipment.WarrantyExpiryService;
import com.toir.service.maintanance.MaintenanceAutomationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NightlyMaintenanceJobWarrantyTest {

    @Mock
    OverdueDetectorService overdueDetectorService;

    @Mock
    CertificationService certificationService;

    @Mock
    MaintenanceAutomationService maintenanceAutomationService;

    @Mock
    OperationalIssueScannerService operationalIssueScannerService;

    @Mock
    WarrantyExpiryService warrantyExpiryService;

    @InjectMocks
    NightlyMaintenanceJob job;

    @Test
    void runNightlyInvokesWarrantyExpiryCheck() {
        when(overdueDetectorService.evaluate()).thenReturn(
                new OverdueDetectorService.EvaluationResult(0, 0, 0, 0, 0, 0, 0));
        when(certificationService.markExpired()).thenReturn(0);
        when(maintenanceAutomationService.evaluateAllCalendarRules())
                .thenReturn(new MaintenanceAutomationService.EvaluationResult(0, 0, 0, 0, 0, 0));
        when(operationalIssueScannerService.scanAll())
                .thenReturn(new OperationalIssueScannerService.ScanResult(0, 0));
        when(warrantyExpiryService.notifyExpiringWarranties()).thenReturn(3);

        job.runNightly();

        verify(warrantyExpiryService).notifyExpiringWarranties();
    }

    @Test
    void runNightlyContinuesWhenWarrantyExpiryCheckFails() {
        when(overdueDetectorService.evaluate()).thenReturn(
                new OverdueDetectorService.EvaluationResult(0, 0, 0, 0, 0, 0, 0));
        when(certificationService.markExpired()).thenReturn(0);
        when(maintenanceAutomationService.evaluateAllCalendarRules())
                .thenReturn(new MaintenanceAutomationService.EvaluationResult(0, 0, 0, 0, 0, 0));
        when(operationalIssueScannerService.scanAll())
                .thenReturn(new OperationalIssueScannerService.ScanResult(0, 0));
        when(warrantyExpiryService.notifyExpiringWarranties())
                .thenThrow(new RuntimeException("notification backend down"));

        job.runNightly();

        verify(warrantyExpiryService).notifyExpiringWarranties();
    }
}
