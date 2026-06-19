package com.toir.service.users;

import com.toir.dto.user.UserFilterRequest;
import com.toir.dto.user.UserDto;
import com.toir.entity.users.User;
import com.toir.enums.UserStatus;
import com.toir.repository.users.RoleRepository;
import com.toir.repository.users.UserRepository;
import com.toir.util.AuditBuilderService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    UserRepository userRepository;

    @Mock
    RoleRepository roleRepository;

    @Mock
    PasswordEncoder passwordEncoder;

    @Mock
    AuditBuilderService auditBuilderService;

    @InjectMocks
    UserService service;

    @Test
    void searchWithBlankTextFallsBackToAllNonDeletedUsers() {
        PageRequest pageable = PageRequest.of(0, 20);
        when(userRepository.searchIds(null, pageable)).thenReturn(Page.empty(pageable));

        Page<?> result = service.search("   ", 0, 20);

        assertThat(result.getTotalElements()).isZero();
        verify(userRepository).searchIds(null, pageable);
        verify(userRepository, never()).findAllWithRolesByIdInAndIsDeletedFalse(any());
    }

    @Test
    void searchPreservesPageOrderFromRepositoryIds() {
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        PageRequest pageable = PageRequest.of(0, 20);
        Page<UUID> idsPage = new PageImpl<>(List.of(firstId, secondId), pageable, 2);
        when(userRepository.searchIds(eq("ali"), eq(pageable))).thenReturn(idsPage);

        User second = user(secondId, "second.user");
        User first = user(firstId, "first.user");
        when(userRepository.findAllWithRolesByIdInAndIsDeletedFalse(List.of(firstId, secondId)))
                .thenReturn(List.of(second, first));

        Page<UserDto> page = service.search("ali", 0, 20);

        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent()).hasSize(2);
        assertThat(page.getContent().get(0).username()).isEqualTo("first.user");
        assertThat(page.getContent().get(1).username()).isEqualTo("second.user");
    }

    @Test
    @SuppressWarnings("unchecked")
    void searchWithFiltersPreservesPageOrderAfterRoleHydration() {
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        PageRequest pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "updatedAt"));
        User firstPageUser = user(firstId, "first.user");
        User secondPageUser = user(secondId, "second.user");
        when(userRepository.findAll(any(Specification.class), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(firstPageUser, secondPageUser), pageable, 2));
        when(userRepository.findAllWithRolesByIdInAndIsDeletedFalse(List.of(firstId, secondId)))
                .thenReturn(List.of(secondPageUser, firstPageUser));

        Page<UserDto> page = service.searchWithFilters(new UserFilterRequest(
                "ali",
                null,
                null,
                null,
                null,
                null,
                UserStatus.ACTIVE,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        ), 0, 20);

        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent()).extracting(UserDto::username)
                .containsExactly("first.user", "second.user");
        verify(userRepository).findAll(any(Specification.class), eq(pageable));
        verify(userRepository).findAllWithRolesByIdInAndIsDeletedFalse(List.of(firstId, secondId));
    }

    @Test
    void searchTrimsSearchTextBeforeRepositoryCall() {
        PageRequest pageable = PageRequest.of(0, 20);
        when(userRepository.searchIds(eq("Ali"), eq(pageable))).thenReturn(Page.empty(pageable));

        service.search("  Ali  ", 0, 20);

        ArgumentCaptor<String> searchCaptor = ArgumentCaptor.forClass(String.class);
        verify(userRepository).searchIds(searchCaptor.capture(), eq(pageable));
        assertThat(searchCaptor.getValue()).isEqualTo("Ali");
    }

    @Test
    void findByIdPopulatesDepartmentIfPresent() {
        UUID userId = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();

        com.toir.entity.Department department = new com.toir.entity.Department();
        department.setId(deptId);
        department.setCode("DEP-TEST");
        department.setName("Test Department");

        User user = user(userId, "test.user");
        user.setDepartmentId(deptId);
        user.setDepartment(department);

        when(userRepository.findByIdAndIsDeletedFalse(userId)).thenReturn(java.util.Optional.of(user));

        UserDto result = service.findById(userId);

        assertThat(result.department()).isNotNull();
        assertThat(result.department().id()).isEqualTo(deptId);
        assertThat(result.department().code()).isEqualTo("DEP-TEST");
        assertThat(result.department().name()).isEqualTo("Test Department");

        verify(userRepository).findByIdAndIsDeletedFalse(userId);
    }

    private User user(UUID id, String username) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setEmail(username + "@example.com");
        user.setFullName(username);
        user.setPhone("+998901100000");
        return user;
    }
}
