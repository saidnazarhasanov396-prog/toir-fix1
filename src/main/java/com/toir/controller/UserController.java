package com.toir.controller;
import com.toir.service.UserService;

import com.toir.security.RequiresAdmin;
import com.toir.dto.user.CreateRoleUserRequest;
import com.toir.dto.user.CreateUserRequest;
import com.toir.dto.user.UpdateUserRequest;
import com.toir.dto.user.UserDto;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "users")
@RequiresAdmin
public class UserController {

    private final UserService service;

    public UserController(UserService service) {
        this.service = service;
    }

    @GetMapping
    public List<UserDto> list() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    public UserDto get(@PathVariable UUID id) {
        return service.findById(id);
    }

    @PostMapping
    public ResponseEntity<UserDto> create(@Valid @RequestBody CreateUserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @PostMapping("/by-role")
    public ResponseEntity<UserDto> createForRole(@Valid @RequestBody CreateRoleUserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createForRole(request));
    }

    @PutMapping("/{id}")
    public UserDto update(@PathVariable UUID id, @Valid @RequestBody UpdateUserRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }
}
