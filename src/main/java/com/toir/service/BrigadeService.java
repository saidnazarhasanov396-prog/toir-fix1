package com.toir.service;
import com.toir.entity.users.Brigade;
import com.toir.entity.users.BrigadeMember;
import com.toir.enums.AuditAction;
import com.toir.enums.AuditModule;
import com.toir.repository.projects.BrigadeMemberRepository;
import com.toir.repository.projects.BrigadeRepository;

import com.toir.dto.brigade.BrigadeDto;
import com.toir.dto.brigade.BrigadeMemberDto;
import com.toir.dto.brigade.BrigadeMemberRequest;
import com.toir.dto.brigade.BrigadeRequest;
import com.toir.exception.RestException;
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
public class BrigadeService {

    private final BrigadeRepository brigadeRepo;
    private final BrigadeMemberRepository memberRepo;
    private final CertificationGuard certificationGuard;
    private final SparePartService sparePartService;
    private final AuditBuilderService auditBuilderService;
    private final AuditSerializationService auditSerializationService;


    @Transactional(readOnly = true)
    public List<BrigadeDto> findAll(UUID departmentId, Boolean activeOnly, String search) {
        List<Brigade> list = brigadeRepo.findAllByDepartmentAndIsActiveOnlyAndDeletedAndSearch
                (departmentId,activeOnly,sparePartService.toSearchPattern(search));
        return list.stream().map(BrigadeDto::from).toList();
    }

    @Transactional(readOnly = true)
    public BrigadeDto findById(UUID id) {
        return BrigadeDto.from(load(id));
    }

    public BrigadeDto create(BrigadeRequest r) {
        if (brigadeRepo.existsByCodeAndIsDeletedFalse(r.code())) {
            throw RestException.conflict("Brigade code already exists: " + r.code());
        }
        Brigade b = new Brigade();
        b.setCode(r.code());
        b.setName(r.name());
        b.setDepartmentId(r.departmentId());
        b.setForemanId(r.foremanId());
        b.setSpecialization(r.specialization());
        if (r.active() != null) b.setActive(r.active());
        Brigade saved = brigadeRepo.save(b);
        auditBrigade(AuditAction.CREATE, saved.getId(), null, saved);
        return BrigadeDto.from(saved);
    }

    public BrigadeDto update(UUID id, BrigadeRequest r) {
        Brigade b = load(id);
        String oldJson = auditSerializationService.toJson(b);
        if (!b.getCode().equals(r.code()) && brigadeRepo.existsByCodeAndIsDeletedFalse(r.code())) {
            throw RestException.conflict("Brigade code already exists: " + r.code());
        }
        b.setCode(r.code());
        b.setName(r.name());
        b.setDepartmentId(r.departmentId());
        b.setForemanId(r.foremanId());
        b.setSpecialization(r.specialization());
        if (r.active() != null) b.setActive(r.active());
        auditBrigade(AuditAction.UPDATE, b.getId(), oldJson, b);
        return BrigadeDto.from(b);
    }

    public void delete(UUID id) {
        Brigade b = load(id);
        String oldJson = auditSerializationService.toJson(b);
        b.setDeleted(true);
        Brigade saved = brigadeRepo.save(b);
        auditBrigade(AuditAction.DELETE, saved.getId(), oldJson, null);
    }

    public BrigadeMemberDto addMember(UUID brigadeId, BrigadeMemberRequest r) {
        Brigade b = load(brigadeId);
        memberRepo.findByBrigadeIdAndUserIdAndIsDeletedFalse(brigadeId, r.userId()).ifPresent(m -> {
            throw RestException.conflict("User is already a member of this brigade");
        });
        certificationGuard.requireForRole(r.userId(), r.roleCode());
        BrigadeMember m = new BrigadeMember();
        m.setBrigade(b);
        m.setUserId(r.userId());
        m.setRoleCode(r.roleCode());
        m.setGrade(r.grade());
        m.setQualifications(r.qualifications());
        if (r.active() != null) m.setActive(r.active());
        BrigadeMember saved = memberRepo.save(m);
        auditMember(AuditAction.CREATE, saved.getId(), null, saved);
        return BrigadeMemberDto.from(saved);
    }

    public void removeMember(UUID brigadeId, UUID memberId) {
        BrigadeMember m = memberRepo.findByIdAndIsDeletedFalse(memberId)
                .orElseThrow(() -> RestException.notFound("Brigade member not found: " + memberId));
        if (m.getBrigade() == null || !m.getBrigade().getId().equals(brigadeId)) {
            throw RestException.badRequest("Member does not belong to this brigade");
        }
        String oldJson = auditSerializationService.toJson(m);
        m.setDeleted(true);
        BrigadeMember saved = memberRepo.save(m);
        auditMember(AuditAction.DELETE, saved.getId(), oldJson, null);
    }

    @Transactional(readOnly = true)
    public List<BrigadeMemberDto> listMembers(UUID brigadeId) {
        load(brigadeId);
        return memberRepo.findAllByBrigadeIdAndIsDeletedFalse(brigadeId).stream().map(BrigadeMemberDto::from).toList();
    }

    private Brigade load(UUID id) {
        return brigadeRepo.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Brigade not found: " + id));
    }

    private void auditBrigade(AuditAction action, UUID id, String oldJson, Brigade current) {
        String newJson = current == null ? null : auditSerializationService.toJson(current);
        auditBuilderService.log(
                "brigade",
                id != null ? id.toString() : null,
                action,
                AuditModule.BRIGADE,
                auditBrigadeMessage(action),
                oldJson,
                newJson
        );
    }

    private void auditMember(AuditAction action, UUID id, String oldJson, BrigadeMember current) {
        String newJson = current == null ? null : auditSerializationService.toJson(current);
        auditBuilderService.log(
                "brigade_member",
                id != null ? id.toString() : null,
                action,
                AuditModule.BRIGADE_MEMBER,
                auditMemberMessage(action),
                oldJson,
                newJson
        );
    }

    private String auditBrigadeMessage(AuditAction action) {
        return switch (action) {
            case CREATE -> "Бригада создана";
            case UPDATE -> "Бригада обновлена";
            case DELETE -> "Бригада удалена";
            default -> "Действие выполнено над бригадой";
        };
    }

    private String auditMemberMessage(AuditAction action) {
        return switch (action) {
            case CREATE -> "Участник бригады добавлен";
            case UPDATE -> "Участник бригады обновлен";
            case DELETE -> "Участник бригады удален";
            default -> "Действие выполнено над участником бригады";
        };
    }
}
