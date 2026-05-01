package com.toir.service;
import com.toir.entity.Brigade;
import com.toir.entity.BrigadeMember;
import com.toir.repository.BrigadeMemberRepository;
import com.toir.repository.BrigadeRepository;
import com.toir.entity.User;

import com.toir.dto.brigade.BrigadeDto;
import com.toir.dto.brigade.BrigadeMemberDto;
import com.toir.dto.brigade.BrigadeMemberRequest;
import com.toir.dto.brigade.BrigadeRequest;
import com.toir.service.CertificationGuard;
import com.toir.exception.RestException;
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


    @Transactional(readOnly = true)
    public List<BrigadeDto> findAll(UUID departmentId, Boolean activeOnly) {
        List<Brigade> list;
        if (departmentId != null) {
            list = brigadeRepo.findAllByDepartmentIdAndIsDeletedFalse(departmentId);
        } else if (Boolean.TRUE.equals(activeOnly)) {
            list = brigadeRepo.findAllByActiveTrueAndIsDeletedFalse();
        } else {
            list = brigadeRepo.findAllByIsDeletedFalse();
        }
        return com.toir.util.UpdatedAtSorter.descending(list).stream().map(BrigadeDto::from).toList();
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
        return BrigadeDto.from(brigadeRepo.save(b));
    }

    public BrigadeDto update(UUID id, BrigadeRequest r) {
        Brigade b = load(id);
        if (!b.getCode().equals(r.code()) && brigadeRepo.existsByCodeAndIsDeletedFalse(r.code())) {
            throw RestException.conflict("Brigade code already exists: " + r.code());
        }
        b.setCode(r.code());
        b.setName(r.name());
        b.setDepartmentId(r.departmentId());
        b.setForemanId(r.foremanId());
        b.setSpecialization(r.specialization());
        if (r.active() != null) b.setActive(r.active());
        return BrigadeDto.from(b);
    }

    public void delete(UUID id) {
        Brigade b = load(id);
        b.setDeleted(true);
        brigadeRepo.save(b);
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
        return BrigadeMemberDto.from(memberRepo.save(m));
    }

    public void removeMember(UUID brigadeId, UUID memberId) {
        BrigadeMember m = memberRepo.findByIdAndIsDeletedFalse(memberId)
                .orElseThrow(() -> RestException.notFound("Brigade member not found: " + memberId));
        if (m.getBrigade() == null || !m.getBrigade().getId().equals(brigadeId)) {
            throw RestException.badRequest("Member does not belong to this brigade");
        }
        m.setDeleted(true);
        memberRepo.save(m);
    }

    @Transactional(readOnly = true)
    public List<BrigadeMemberDto> listMembers(UUID brigadeId) {
        load(brigadeId);
        return com.toir.util.UpdatedAtSorter.descending(memberRepo.findAllByBrigadeIdAndIsDeletedFalse(brigadeId)).stream().map(BrigadeMemberDto::from).toList();
    }

    private Brigade load(UUID id) {
        return brigadeRepo.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Brigade not found: " + id));
    }
}
