package com.toir.config;

import com.toir.entity.equipment.Equipment;
import com.toir.entity.users.Employee;
import com.toir.entity.users.User;
import com.toir.repository.CriticalityClassRepository;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.repository.users.EmployeeRepository;
import com.toir.repository.users.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SampleDataSeederTest {

    @Mock
    private CriticalityClassRepository criticalityClassRepository;
    @Mock
    private EquipmentRepository equipmentRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private EmployeeRepository employeeRepository;

    @InjectMocks
    private SampleDataSeeder seeder;

    @Test
    void exactEmployeeMatchStoresEmployeeIdAsEquipmentResponsible() {
        UUID userId = UUID.randomUUID();
        UUID employeeId = UUID.randomUUID();
        Employee employee = employee(employeeId);
        Equipment equipment = seedEquipment(userId, List.of(employee));

        assertThat(equipment.getResponsibleId()).isEqualTo(employeeId);
        verify(employeeRepository).findAllByUserIdAndIsDeletedFalse(userId);
    }

    @Test
    void noEmployeeMatchStoresNullWithoutFallingBackToUserId() {
        UUID userId = UUID.randomUUID();
        Equipment equipment = seedEquipment(userId, List.of());

        assertThat(equipment.getResponsibleId()).isNull();
        verify(employeeRepository).findAllByUserIdAndIsDeletedFalse(userId);
    }

    @Test
    void multipleEmployeeMatchesStoreNullWithoutFallingBackToUserId() {
        UUID userId = UUID.randomUUID();
        Equipment equipment = seedEquipment(userId, List.of(
                employee(UUID.randomUUID()),
                employee(UUID.randomUUID())
        ));

        assertThat(equipment.getResponsibleId()).isNull();
        verify(employeeRepository).findAllByUserIdAndIsDeletedFalse(userId);
    }

    private Equipment seedEquipment(UUID userId, List<Employee> employees) {
        User admin = new User();
        admin.setId(userId);
        Equipment equipment = new Equipment();
        equipment.setCode("TEST-EQUIPMENT");

        when(criticalityClassRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of());
        when(userRepository.findByUsernameAndIsDeletedFalse("admin")).thenReturn(Optional.of(admin));
        when(employeeRepository.findAllByUserIdAndIsDeletedFalse(userId)).thenReturn(employees);

        ReflectionTestUtils.invokeMethod(seeder, "linkEquipmentCriticalityAndResponsible", List.of(equipment));

        verify(equipmentRepository).save(equipment);
        return equipment;
    }

    private Employee employee(UUID id) {
        Employee employee = new Employee();
        employee.setId(id);
        return employee;
    }
}
