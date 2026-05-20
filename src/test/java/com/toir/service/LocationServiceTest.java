package com.toir.service;

import com.toir.dto.location.LocationDto;
import com.toir.dto.location.LocationRequest;
import com.toir.entity.Location;
import com.toir.enums.LocationType;
import com.toir.repository.LocationRepository;
import com.toir.util.AuditBuilderService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Year;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LocationServiceTest {

    @Mock
    LocationRepository repository;

    @Mock
    AuditBuilderService auditBuilderService;

    @InjectMocks
    LocationService service;

    @Test
    void createWithoutCodeStillGeneratesCode() {
        int year = Year.now().getValue();
        String codePrefix = "LOC-" + year + "-";
        String expectedCode = "LOC-" + year + "-0001";

        when(repository.maxSequenceByCodePrefix(codePrefix)).thenReturn(0L);
        when(repository.existsByCode(expectedCode)).thenReturn(false);
        when(repository.save(any(Location.class))).thenAnswer(invocation -> {
            Location location = invocation.getArgument(0);
            ReflectionTestUtils.setField(location, "id", UUID.randomUUID());
            return location;
        });

        LocationDto created = service.create(request());

        assertThat(created.code()).isEqualTo(expectedCode);
        verify(repository).maxSequenceByCodePrefix(codePrefix);
    }

    @Test
    void generatedCodeIsUnique() {
        int year = Year.now().getValue();
        String codePrefix = "LOC-" + year + "-";
        String firstCandidate = "LOC-" + year + "-0001";
        String secondCandidate = "LOC-" + year + "-0002";

        when(repository.maxSequenceByCodePrefix(codePrefix)).thenReturn(0L);
        when(repository.existsByCode(firstCandidate)).thenReturn(true);
        when(repository.existsByCode(secondCandidate)).thenReturn(false);
        when(repository.save(any(Location.class))).thenAnswer(invocation -> {
            Location location = invocation.getArgument(0);
            ReflectionTestUtils.setField(location, "id", UUID.randomUUID());
            return location;
        });

        LocationDto created = service.create(request());

        assertThat(created.code()).isEqualTo(secondCandidate);
        verify(repository).existsByCode(firstCandidate);
        verify(repository).existsByCode(secondCandidate);
    }

    @Test
    void duplicateCodeDoesNotReturn500() {
        int year = Year.now().getValue();
        String codePrefix = "LOC-" + year + "-";
        String firstCandidate = "LOC-" + year + "-0001";
        String secondCandidate = "LOC-" + year + "-0002";

        when(repository.maxSequenceByCodePrefix(codePrefix)).thenReturn(0L);
        when(repository.existsByCode(firstCandidate)).thenReturn(false);
        when(repository.existsByCode(secondCandidate)).thenReturn(false);
        when(repository.save(any(Location.class)))
                .thenThrow(new DataIntegrityViolationException(
                        "duplicate key value violates unique constraint \"locations_code_key\""))
                .thenAnswer(invocation -> {
                    Location location = invocation.getArgument(0);
                    ReflectionTestUtils.setField(location, "id", UUID.randomUUID());
                    return location;
                });

        LocationDto created = service.create(request());

        assertThat(created.code()).isEqualTo(secondCandidate);
        verify(repository, times(2)).save(any(Location.class));
    }

    @Test
    void responseIncludesGeneratedCode() {
        int year = Year.now().getValue();
        String expectedCode = "LOC-" + year + "-0001";

        when(repository.maxSequenceByCodePrefix("LOC-" + year + "-")).thenReturn(0L);
        when(repository.existsByCode(expectedCode)).thenReturn(false);
        when(repository.save(any(Location.class))).thenAnswer(invocation -> {
            Location location = invocation.getArgument(0);
            ReflectionTestUtils.setField(location, "id", UUID.randomUUID());
            return location;
        });

        LocationDto created = service.create(request());

        assertThat(created.code()).isEqualTo(expectedCode);
        ArgumentCaptor<Location> captor = ArgumentCaptor.forClass(Location.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getCode()).isEqualTo(expectedCode);
    }

    private LocationRequest request() {
        return new LocationRequest(
                null,
                "Главная мастерская",
                "Asosiy ustaxona",
                "Main workshop",
                LocationType.WORKSHOP,
                null,
                UUID.randomUUID(),
                "Primary location"
        );
    }
}
