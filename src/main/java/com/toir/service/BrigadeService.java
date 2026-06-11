package com.toir.service;

import com.toir.dto.brigade.BrigadeDto;
import com.toir.dto.brigade.BrigadeMemberDto;
import com.toir.dto.brigade.BrigadeMemberRequest;
import com.toir.dto.brigade.BrigadeRequest;
import com.toir.entity.users.Brigade;
import com.toir.entity.users.BrigadeMember;
import com.toir.enums.AuditAction;
import com.toir.enums.AuditModule;
import com.toir.exception.RestException;
import com.toir.repository.projects.BrigadeMemberRepository;
import com.toir.repository.projects.BrigadeRepository;
import com.toir.util.AuditBuilderService;
import com.toir.util.CodeGenerationUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BrigadeService {

    private final BrigadeRepository brigadeRepo;
    private final BrigadeMemberRepository memberRepo;
    private final CertificationGuard certificationGuard;
    private final SparePartService sparePartService;
    private final AuditBuilderService auditBuilderService;


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

    @Transactional
    public BrigadeDto create(BrigadeRequest r) {
        CodeGenerationUtils.rejectClientProvidedCode(r.code());
        Brigade b = new Brigade();
        b.setCode(nextCode());
        b.setName(r.name());
        b.setDepartmentId(r.departmentId());
        b.setForemanId(r.foremanId());
        b.setSpecialization(r.specialization());
        if (r.active() != null) b.setActive(r.active());
        Brigade saved = brigadeRepo.save(b);

        auditBuilderService.log(
                "brigade",
                saved.getId().toString(),
                AuditAction.CREATE,
                AuditModule.BRIGADE,
                "Бригада создана",
                null,
                saved
        );
        return BrigadeDto.from(saved);
    }

    @Transactional
    public BrigadeDto update(UUID id, BrigadeRequest r) {
        CodeGenerationUtils.rejectClientProvidedCode(r.code());
        Brigade b = load(id);
        b.setName(r.name());
        b.setDepartmentId(r.departmentId());
        b.setForemanId(r.foremanId());
        b.setSpecialization(r.specialization());
        if (r.active() != null) b.setActive(r.active());

        Brigade saved = brigadeRepo.save(b);

        auditBuilderService.log(
                "brigade",
                saved.getId().toString(),
                AuditAction.UPDATE,
                AuditModule.BRIGADE,
                "Бригада обновлена",
                b,
                saved
        );
        return BrigadeDto.from(b);
    }

    private String nextCode() {
        String prefix = "BR-" + java.time.Year.now().getValue() + "-";
        return CodeGenerationUtils.nextYearSequenceCode(
                "BR",
                () -> brigadeRepo.maxSequenceByCodePrefix(prefix),
                brigadeRepo::existsByCodeAndIsDeletedFalse
        );
    }

    @Transactional
    public void delete(UUID id) {
        Brigade b = load(id);
        b.setDeleted(true);
        Brigade saved = brigadeRepo.save(b);

        auditBuilderService.log(
                "brigade",
                id != null ? id.toString() : null,
                AuditAction.DELETE,
                AuditModule.BRIGADE,
                "Бригада удалена",
                saved,
                null
        );


    }

    @Transactional
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

        auditBuilderService.log(
                "brigade_member",
                saved.getId().toString(),
                AuditAction.CREATE,
                AuditModule.BRIGADE_MEMBER,
                "Участник бригады добавлен",
                null,
                saved
        );

        return BrigadeMemberDto.from(saved);
    }

    @Transactional
    public void removeMember(UUID brigadeId, UUID memberId) {
        BrigadeMember m = memberRepo.findByIdAndIsDeletedFalse(memberId)
                .orElseThrow(() -> RestException.notFound("Brigade member not found: " + memberId));
        if (m.getBrigade() == null || !m.getBrigade().getId().equals(brigadeId)) {
            throw RestException.badRequest("Member does not belong to this brigade");
        }
        m.setDeleted(true);
        BrigadeMember saved = memberRepo.save(m);


        auditBuilderService.log(
                "brigade_member",
                saved.getId().toString(),
                AuditAction.DELETE,
                AuditModule.BRIGADE_MEMBER,
                "Участник бригады удален",
                saved,
                null
        );
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
}
