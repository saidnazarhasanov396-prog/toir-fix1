package com.toir.role;

import com.toir.common.exception.RestException;
import com.toir.role.dto.RoleDto;
import com.toir.role.dto.RoleRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class RoleService {

    private final RoleRepository repository;

    public RoleService(RoleRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<RoleDto> findAll() {
        return repository.findAll().stream().map(RoleDto::from).toList();
    }

    @Transactional(readOnly = true)
    public RoleDto findById(UUID id) {
        return RoleDto.from(getOrThrow(id));
    }

    public RoleDto create(RoleRequest request) {
        if (repository.existsByCode(request.code())) {
            throw RestException.conflict("Role with code " + request.code() + " already exists");
        }
        Role role = new Role();
        role.setCode(request.code());
        role.setName(request.name());
        role.setDescription(request.description());
        role.setPermissions(request.permissions());
        role.setSystem(false);
        return RoleDto.from(repository.save(role));
    }

    public RoleDto update(UUID id, RoleRequest request) {
        Role role = getOrThrow(id);
        if (role.isSystem()) {
            throw RestException.forbidden("System roles cannot be modified");
        }
        role.setName(request.name());
        role.setDescription(request.description());
        role.setPermissions(request.permissions());
        return RoleDto.from(role);
    }

    public void delete(UUID id) {
        Role role = getOrThrow(id);
        if (role.isSystem()) {
            throw RestException.forbidden("System roles cannot be deleted");
        }
        repository.delete(role);
    }

    private Role getOrThrow(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> RestException.notFound("Role not found: " + id));
    }
}
