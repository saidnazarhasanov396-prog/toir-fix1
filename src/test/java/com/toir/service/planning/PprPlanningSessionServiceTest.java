package com.toir.service.planning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.toir.dto.pprplanning.PprPlanningSessionCreateRequest;
import com.toir.entity.planning.PprPlanningSession;
import com.toir.enums.planning.PprPlanningSessionStatus;
import com.toir.repository.planning.PprPlanningSessionRepository;
import com.toir.repository.planning.PprPlanningVariantItemRepository;
import com.toir.repository.planning.PprPlanningVariantRepository;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PprPlanningSessionServiceTest {

    @Mock PprPlanningSessionRepository sessionRepository;
    @Mock PprPlanningVariantRepository variantRepository;
    @Mock PprPlanningVariantItemRepository itemRepository;
    @InjectMocks PprPlanningSessionService service;

    @Test
    void createStartsAnAnnualDraftSession() {
        UUID departmentId = UUID.randomUUID();
        when(sessionRepository.save(any(PprPlanningSession.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PprPlanningSession session = service.create(new PprPlanningSessionCreateRequest(
                "Annual PPR 2027",
                2027,
                departmentId,
                LocalDate.of(2027, 1, 1),
                LocalDate.of(2027, 12, 31),
                "Base planning session"));

        assertThat(session.getStatus()).isEqualTo(PprPlanningSessionStatus.DRAFT);
        assertThat(session.getDepartmentId()).isEqualTo(departmentId);
        assertThat(session.getYear()).isEqualTo(2027);
    }
}
