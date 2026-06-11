package com.toir.service;

import com.toir.dto.location.LocationDto;
import com.toir.dto.location.LocationRequest;
import com.toir.entity.Department;
import com.toir.entity.Location;
import com.toir.enums.LocationType;
import com.toir.repository.LocationRepository;
import com.toir.repository.department.DepartmentRepository;
import com.toir.util.AuditBuilderService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Year;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LocationServiceTest {

    @Mock
    LocationRepository repository;

    @Mock
    DepartmentRepository departmentRepository;

    @Mock
    AuditBuilderService auditBuilderService;

    @InjectMocks
    LocationService service;

    @Test
    void createWithoutCodeStillGeneratesCode() {
        int year = Year.now().getValue();
        String codePrefix = "LOC-" + year + "-";
        String expectedCode = "LOC-" + year + "-0001";
        LocationRequest request = request();

        when(repository.maxSequenceByCodePrefix(codePrefix)).thenReturn(0L);
        when(repository.existsByCode(expectedCode)).thenReturn(false);
        when(repository.save(any(Location.class))).thenAnswer(invocation -> {
            Location location = invocation.getArgument(0);
            ReflectionTestUtils.setField(location, "id", UUID.randomUUID());
            return location;
        });
        when(departmentRepository.findByIdAndIsDeletedFalse(request.departmentId()))
                .thenReturn(Optional.of(department(request.departmentId(), "Maintenance")));

        LocationDto created = service.create(request);

        assertThat(created.code()).isEqualTo(expectedCode);
        assertThat(created.departmentName()).isEqualTo("Maintenance");
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

    @Test
    void findAllIncludesDepartmentName() {
        UUID departmentId = UUID.randomUUID();
        Location location = location(departmentId);

        when(repository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of(location));
        when(departmentRepository.findAllByIdInAndIsDeletedFalse(List.of(departmentId)))
                .thenReturn(List.of(department(departmentId, "Maintenance")));

        List<LocationDto> result = service.findAll();

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().departmentId()).isEqualTo(departmentId);
        assertThat(result.getFirst().departmentName()).isEqualTo("Maintenance");
    }

    @Test
    void searchFiltersByDepartmentId() {
        UUID departmentId = UUID.randomUUID();
        Location location = location(departmentId);
        Page<Location> page = new PageImpl<>(List.of(location));

        when(repository.search(eq(LocationType.WORKSHOP), eq("main"), eq(departmentId), any(Pageable.class)))
                .thenReturn(page);
        when(departmentRepository.findAllByIdInAndIsDeletedFalse(List.of(departmentId)))
                .thenReturn(List.of(department(departmentId, "Maintenance")));

        Page<LocationDto> result = service.search(LocationType.WORKSHOP, "main", departmentId, 0, 20);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().getFirst().departmentId()).isEqualTo(departmentId);
        verify(repository).search(eq(LocationType.WORKSHOP), eq("main"), eq(departmentId), any(Pageable.class));
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

    private Location location(UUID departmentId) {
        Location location = new Location();
        ReflectionTestUtils.setField(location, "id", UUID.randomUUID());
        location.setCode("LOC-2026-0001");
        location.setName("Main workshop");
        location.setType(LocationType.WORKSHOP);
        location.setDepartmentId(departmentId);
        return location;
    }

    private Department department(UUID id, String name) {
        Department department = new Department();
        ReflectionTestUtils.setField(department, "id", id);
        department.setName(name);
        return department;
    }
}
