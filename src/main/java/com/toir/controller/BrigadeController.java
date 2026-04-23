package com.toir.controller;
import com.toir.service.BrigadeService;

import com.toir.dto.brigade.BrigadeDto;
import com.toir.dto.brigade.BrigadeMemberDto;
import com.toir.dto.brigade.BrigadeMemberRequest;
import com.toir.dto.brigade.BrigadeRequest;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/brigades")
@Tag(name = "brigades")
public class BrigadeController {

    private final BrigadeService service;

    public BrigadeController(BrigadeService service) {
        this.service = service;
    }

    @GetMapping
    public List<BrigadeDto> list(
            @RequestParam(required = false) UUID departmentId,
            @RequestParam(required = false) Boolean activeOnly
    ) {
        return service.findAll(departmentId, activeOnly);
    }

    @GetMapping("/{id}")
    public BrigadeDto get(@PathVariable UUID id) { return service.findById(id); }

    @PostMapping
    public ResponseEntity<BrigadeDto> create(@Valid @RequestBody BrigadeRequest r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(r));
    }

    @PutMapping("/{id}")
    public BrigadeDto update(@PathVariable UUID id, @Valid @RequestBody BrigadeRequest r) {
        return service.update(id, r);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/members")
    public List<BrigadeMemberDto> listMembers(@PathVariable UUID id) {
        return service.listMembers(id);
    }

    @PostMapping("/{id}/members")
    public ResponseEntity<BrigadeMemberDto> addMember(@PathVariable UUID id, @Valid @RequestBody BrigadeMemberRequest r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.addMember(id, r));
    }

    @DeleteMapping("/{id}/members/{memberId}")
    public ResponseEntity<Void> removeMember(@PathVariable UUID id, @PathVariable UUID memberId) {
        service.removeMember(id, memberId);
        return ResponseEntity.noContent().build();
    }
}
