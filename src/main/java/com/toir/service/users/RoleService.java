package com.toir.service.users;
import com.toir.entity.users.Role;
import com.toir.enums.AuditAction;
import com.toir.enums.AuditModule;

import com.toir.exception.RestException;
import com.toir.dto.role.RoleDto;
import com.toir.dto.role.RoleRequest;
import com.toir.repository.users.RoleRepository;
import com.toir.util.AuditBuilderService;
import com.toir.util.AuditSerializationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class RoleService {

    private final RoleRepository repository;
    private final AuditBuilderService auditBuilderService;
    private final AuditSerializationService auditSerializationService;


    @Transactional(readOnly = true)
    public List<RoleDto> findAll() {
        return repository.findAllByIsDeletedFalseOrderByUpdatedAtDesc().stream().map(RoleDto::from).toList();
    }

    @Transactional(readOnly = true)
    public RoleDto findById(UUID id) {
        return RoleDto.from(getOrThrow(id));
    }

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
        audit(AuditAction.CREATE, saved.getId(), null, saved);
        return RoleDto.from(saved);
    }

    public RoleDto update(UUID id, RoleRequest request) {
        Role role = getOrThrow(id);
        if (role.isSystem()) {
            throw RestException.forbidden("System roles cannot be modified");
        }
        String oldJson = auditSerializationService.toJson(role);
        role.setName(request.name());
        role.setDescription(request.description());
        role.setPermissions(request.permissions());
        audit(AuditAction.UPDATE, role.getId(), oldJson, role);
        return RoleDto.from(role);
    }

    public void delete(UUID id) {
        Role role = getOrThrow(id);
        if (role.isSystem()) {
            throw RestException.forbidden("System roles cannot be deleted");
        }
        String oldJson = auditSerializationService.toJson(role);
        role.setDeleted(true);
        Role saved = repository.save(role);
        audit(AuditAction.DELETE, saved.getId(), oldJson, null);
    }

    private Role getOrThrow(UUID id) {
        return repository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Role not found: " + id));
    }

    private void audit(AuditAction action, UUID id, String oldJson, Role current) {
        String newJson = current == null ? null : auditSerializationService.toJson(current);
        auditBuilderService.log(
                "role",
                id != null ? id.toString() : null,
                action,
                AuditModule.ROLE,
                auditMessage(action),
                oldJson,
                newJson
        );
    }

    private String auditMessage(AuditAction action) {
        return switch (action) {
            case CREATE -> "Роль создана";
            case UPDATE -> "Роль обновлена";
            case DELETE -> "Роль удалена";
            default -> "Действие выполнено над ролью";
        };
    }
}
