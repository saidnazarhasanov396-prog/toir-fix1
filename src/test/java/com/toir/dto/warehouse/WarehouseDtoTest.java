package com.toir.dto.warehouse;

import com.toir.entity.Department;
import com.toir.entity.Employee;
import com.toir.entity.Location;
import com.toir.entity.Warehouse;
import com.toir.repository.DepartmentRepository;
import com.toir.repository.EmployeeRepository;
import com.toir.repository.LocationRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WarehouseDtoTest {

    @Test
    void fromWithStocksKeepsFlatIdsAndBuildsReferences() {
        UUID departmentId = UUID.randomUUID();
        UUID locationId = UUID.randomUUID();
        UUID responsibleId = UUID.randomUUID();

        Warehouse warehouse = new Warehouse();
        warehouse.setId(UUID.randomUUID());
        warehouse.setCode("WH-001");
        warehouse.setName("Main warehouse");
        warehouse.setDepartmentId(departmentId);
        warehouse.setLocationId(locationId);
        warehouse.setResponsibleId(responsibleId);
        warehouse.setActive(true);

        Department department = new Department();
        department.setId(departmentId);
        department.setCode("DEP-001");
        department.setName("Maintenance");

        Location location = new Location();
        location.setId(locationId);
        location.setCode("LOC-001");
        location.setName("Plant 1");

        Employee responsible = new Employee();
        responsible.setId(responsibleId);
        responsible.setLastName("Karimov");
        responsible.setFirstName("Ali");
        responsible.setMiddleName("Valiyevich");

        DepartmentRepository departmentRepository = mock(DepartmentRepository.class);
        LocationRepository locationRepository = mock(LocationRepository.class);
        EmployeeRepository employeeRepository = mock(EmployeeRepository.class);

        when(departmentRepository.findByIdAndIsDeletedFalse(departmentId)).thenReturn(Optional.of(department));
        when(locationRepository.findByIdAndIsDeletedFalse(locationId)).thenReturn(Optional.of(location));
        when(employeeRepository.findByIdAndIsDeletedFalse(responsibleId)).thenReturn(Optional.of(responsible));

        WarehouseDto dto = WarehouseDto.fromWithStocks(
                warehouse,
                List.of(),
                departmentRepository,
                locationRepository,
                employeeRepository
        );

        assertThat(dto.departmentId()).isEqualTo(departmentId);
        assertThat(dto.locationId()).isEqualTo(locationId);
        assertThat(dto.responsibleId()).isEqualTo(responsibleId);
        assertThat(dto.department().code()).isEqualTo("DEP-001");
        assertThat(dto.location().code()).isEqualTo("LOC-001");
        assertThat(dto.responsible().fullName()).isEqualTo("Karimov Ali Valiyevich");
    }
}
