package com.toir.service;

import com.toir.dto.laborentry.LaborEntryDto;
import com.toir.entity.LaborEntry;
import com.toir.entity.users.User;
import com.toir.enums.UserStatus;
import com.toir.repository.LaborEntryRepository;
import com.toir.repository.users.UserRepository;
import com.toir.util.AuditBuilderService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LaborEntryServiceTest {

    @Mock
    LaborEntryRepository repository;

    @Mock
    UserRepository userRepository;

    @Mock
    AuditBuilderService auditBuilderService;

    @InjectMocks
    LaborEntryService service;

    @Test
    void enrichesLaborEntriesWithUserObject() {
        UUID workOrderId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        LaborEntry entry = laborEntry(workOrderId, userId, null, "Internal labor");
        User user = user(userId, "Ali Worker", "ali.worker");

        when(repository.findAllByWorkOrderIdAndIsDeletedFalseOrderByWorkDateAsc(workOrderId)).thenReturn(List.of(entry));
        when(userRepository.findAllByIdInAndIsDeletedFalse(org.mockito.ArgumentMatchers.anyCollection()))
                .thenReturn(List.of(user));

        List<LaborEntryDto> result = service.findByWorkOrder(workOrderId);

        assertThat(result).hasSize(1);
        LaborEntryDto dto = result.get(0);
        assertThat(dto.userId()).isEqualTo(userId);
        assertThat(dto.user()).isNotNull();
        assertThat(dto.user().id()).isEqualTo(userId);
        assertThat(dto.user().fullName()).isEqualTo("Ali Worker");
        assertThat(dto.user().username()).isEqualTo("ali.worker");
        assertThat(dto.user().email()).isEqualTo("ali.worker@example.com");
        assertThat(dto.user().phone()).isEqualTo("+998900000001");
        assertThat(dto.user().status()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    void contractorOnlyRowsDoNotLookupUser() {
        UUID workOrderId = UUID.randomUUID();
        LaborEntry contractorRow = laborEntry(workOrderId, null, "Vendor X", "Contractor labor");
        when(repository.findAllByWorkOrderIdAndIsDeletedFalseOrderByWorkDateAsc(workOrderId))
                .thenReturn(List.of(contractorRow));

        List<LaborEntryDto> result = service.findByWorkOrder(workOrderId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).user()).isNull();
        assertThat(result.get(0).userId()).isNull();
        verify(userRepository, never()).findAllByIdInAndIsDeletedFalse(org.mockito.ArgumentMatchers.anyCollection());
    }

    @Test
    void missingDeletedUserDoesNotThrow() {
        UUID workOrderId = UUID.randomUUID();
        UUID missingUserId = UUID.randomUUID();
        LaborEntry legacyRow = laborEntry(workOrderId, missingUserId, null, "Legacy");

        when(repository.findAllByWorkOrderIdAndIsDeletedFalseOrderByWorkDateAsc(workOrderId))
                .thenReturn(List.of(legacyRow));
        when(userRepository.findAllByIdInAndIsDeletedFalse(org.mockito.ArgumentMatchers.anyCollection()))
                .thenReturn(List.of());

        List<LaborEntryDto> result = service.findByWorkOrder(workOrderId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).userId()).isEqualTo(missingUserId);
        assertThat(result.get(0).user()).isNull();
    }

    @Test
    void batchLoadsUsersOnce() {
        UUID workOrderId = UUID.randomUUID();
        UUID userId1 = UUID.randomUUID();
        UUID userId2 = UUID.randomUUID();
        LaborEntry row1 = laborEntry(workOrderId, userId1, null, "r1");
        LaborEntry row2 = laborEntry(workOrderId, userId1, null, "r2");
        LaborEntry row3 = laborEntry(workOrderId, userId2, null, "r3");

        when(repository.findAllByWorkOrderIdAndIsDeletedFalseOrderByWorkDateAsc(workOrderId))
                .thenReturn(List.of(row1, row2, row3));
        when(userRepository.findAllByIdInAndIsDeletedFalse(org.mockito.ArgumentMatchers.anyCollection()))
                .thenReturn(List.of(user(userId1, "U1", "u1"), user(userId2, "U2", "u2")));

        service.findByWorkOrder(workOrderId);

        ArgumentCaptor<Collection<UUID>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(userRepository, times(1)).findAllByIdInAndIsDeletedFalse(captor.capture());
        assertThat(captor.getValue()).containsExactlyInAnyOrder(userId1, userId2);
    }

    private LaborEntry laborEntry(UUID workOrderId, UUID userId, String contractorName, String description) {
        LaborEntry entry = new LaborEntry();
        entry.setId(UUID.randomUUID());
        entry.setWorkOrderId(workOrderId);
        entry.setUserId(userId);
        entry.setContractorName(contractorName);
        entry.setWorkDate(LocalDate.of(2026, 5, 15));
        entry.setHours(2.5);
        entry.setRate(150000.0);
        entry.setDescription(description);
        return entry;
    }

    private User user(UUID id, String fullName, String username) {
        User user = new User();
        user.setId(id);
        user.setFullName(fullName);
        user.setUsername(username);
        user.setEmail(username + "@example.com");
        user.setPhone("+998900000001");
        user.setStatus(UserStatus.ACTIVE);
        return user;
    }
}
