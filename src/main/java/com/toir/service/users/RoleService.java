package com.toir.service.users;

import com.toir.dto.role.RoleDto;
import com.toir.dto.role.RoleRequest;
import com.toir.entity.users.Role;
import com.toir.enums.AuditAction;
import com.toir.enums.AuditModule;
import com.toir.exception.RestException;
import com.toir.repository.users.RoleRepository;
import com.toir.util.AuditBuilderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RoleService {

    private final RoleRepository repository;
    private final AuditBuilderService auditBuilderService;


    @Transactional(readOnly = true)
    public List<RoleDto> findAll() {
        return repository.findAllByIsDeletedFalseOrderByUpdatedAtDesc().stream().map(RoleDto::from).toList();
    }

    @Transactional(readOnly = true)
    public RoleDto findById(UUID id) {
        return RoleDto.from(getOrThrow(id));
    }

    @Transactional
    public RoleDto create(RoleRequest request) {
        if (repository.existsByCodeAndIsDeletedFalse(request.code())) {
            throw RestException.conflict("Role with code " + request.code() + " already exists");
        }
        Role role = new Role();
        role.setCode(request.code());
        role.setName(request.name());
        role.setDescription(request.description());
        role.setPermissions(request.permissions());
        role.setSystem(false);
        Role saved = repository.save(role);

        auditBuilderService.log(
                "role",
                saved.getId().toString(),
                AuditAction.CREATE,
                AuditModule.ROLE,
                "Роль создана",
                null,
                saved
        );
        return RoleDto.from(saved);
    }

    @Transactional
    public RoleDto update(UUID id, RoleRequest request) {
        Role role = getOrThrow(id);
        if (role.isSystem()) {
            throw RestException.forbidden("System roles cannot be modified");
        }
        role.setName(request.name());
        role.setDescription(request.description());
        role.setPermissions(request.permissions());

        Role save = repository.save(role);

        auditBuilderService.log(
                "role",
                save.getId().toString(),
                AuditAction.UPDATE,
                AuditModule.ROLE,
                "Роль обновлена",
                role,
                save
        );
        return RoleDto.from(role);
    }

    @Transactional
    public void delete(UUID id) {
        Role role = getOrThrow(id);
        if (role.isSystem()) {
            throw RestException.forbidden("System roles cannot be deleted");
        }
        role.setDeleted(true);
        Role saved = repository.save(role);

        auditBuilderService.log(
                "role",
                saved.getId().toString(),
                AuditAction.DELETE,
                AuditModule.ROLE,
                "Роль удалена",
                role,
                null
        );
    }

    private Role getOrThrow(UUID id) {
        return repository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Role not found: " + id));
    }

}
