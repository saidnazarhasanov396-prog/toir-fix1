package com.toir.service.maintanance;

import com.toir.dto.maintenanceaction.MaintenanceActionDto;
import com.toir.dto.maintenanceaction.MaintenanceActionRequest;
import com.toir.entity.maintenance.MaintenanceAction;
import com.toir.repository.maintenance.MaintenanceActionRepository;
import com.toir.service.SparePartService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Year;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MaintenanceActionServiceTest {

    @Mock
    MaintenanceActionRepository repository;

    @Mock
    SparePartService sparePartService;

    @InjectMocks
    MaintenanceActionService service;

    @Test
    void createGeneratesBackendCodeAndStoresFreeTextCategory() {
        int year = Year.now().getValue();
        String codePrefix = "MA-" + year + "-";
        String expectedCode = "MA-" + year + "-0001";

        when(repository.maxSequenceByCodePrefix(codePrefix)).thenReturn(0L);
        when(repository.save(any(MaintenanceAction.class))).thenAnswer(invocation -> {
            MaintenanceAction action = invocation.getArgument(0);
            action.setId(UUID.randomUUID());
            return action;
        });

        MaintenanceActionDto created = service.create(request(" Inspection "));

        assertThat(created.code()).isEqualTo(expectedCode);
        assertThat(created.category()).isEqualTo("Inspection");

        ArgumentCaptor<MaintenanceAction> captor = ArgumentCaptor.forClass(MaintenanceAction.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getCode()).isEqualTo(expectedCode);
        assertThat(captor.getValue().getCategory()).isEqualTo("Inspection");
    }

    @Test
    void duplicateGeneratedCodeRetriesNextCandidate() {
        int year = Year.now().getValue();
        String codePrefix = "MA-" + year + "-";
        String firstCandidate = "MA-" + year + "-0001";
        String secondCandidate = "MA-" + year + "-0002";

        when(repository.maxSequenceByCodePrefix(codePrefix)).thenReturn(0L);
        when(repository.save(any(MaintenanceAction.class)))
                .thenThrow(new DataIntegrityViolationException(
                        "duplicate key value violates unique constraint \"uq_maintenance_actions_code_active\""))
                .thenAnswer(invocation -> {
                    MaintenanceAction action = invocation.getArgument(0);
                    action.setId(UUID.randomUUID());
                    return action;
                });
        when(repository.existsByCodeIgnoreCaseAndIsDeletedFalse(firstCandidate)).thenReturn(true);

        MaintenanceActionDto created = service.create(request("Mechanical"));

        assertThat(created.code()).isEqualTo(secondCandidate);
        verify(repository).existsByCodeIgnoreCaseAndIsDeletedFalse(firstCandidate);
        verify(repository, times(2)).save(any(MaintenanceAction.class));
    }

    private MaintenanceActionRequest request(String category) {
        return new MaintenanceActionRequest(
                "Pump inspection",
                category,
                1.5,
                "Mechanic",
                "Lock out before inspection",
                "Wrench",
                "Seal kit",
                "Grease",
                true
        );
    }
}
