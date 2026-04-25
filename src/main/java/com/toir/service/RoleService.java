package com.toir.service;
import com.toir.entity.Role;
import com.toir.repository.RoleRepository;

import com.toir.exception.RestException;
import com.toir.dto.role.RoleDto;
import com.toir.dto.role.RoleRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RoleService {

    private final RoleRepository repository;


    @Transactional(readOnly = true)
    public List<RoleDto> findAll() {
        return repository.findAllByIsDeletedFalse().stream().map(RoleDto::from).toList();
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
        role.setDeleted(true);
        repository.save(role);
    }

    private Role getOrThrow(UUID id) {
        return repository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Role not found: " + id));
    }
}
