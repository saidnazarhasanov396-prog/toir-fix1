package com.toir.service;
import com.toir.repository.UserCertificationRepository;
import com.toir.entity.User;

import com.toir.exception.RestException;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Централизованная проверка допусков сотрудника. Используется при назначении
 * на бригадную позицию / наряд — ролям сопоставляются обязательные типы
 * сертификатов. При отсутствии активного допуска бросает 409.
 */
@Component
public class CertificationGuard {

    /** Обязательные сертификаты по роли в бригаде. */
    private static final Map<String, List<String>> REQUIRED_BY_ROLE = Map.of(
            "WELDER", List.of("CERT-WELD-NAKS"),
            "ELECTRICIAN", List.of("CERT-ELECTRO-V"),
            "HEIGHT_WORKER", List.of("CERT-HEIGHT"),
            "NDT_INSPECTOR", List.of("CERT-NDT")
    );

    private final UserCertificationRepository repo;

    public CertificationGuard(UserCertificationRepository repo) {
        this.repo = repo;
    }

    public void requireForRole(UUID userId, String roleCode) {
        List<String> required = REQUIRED_BY_ROLE.getOrDefault(roleCode, List.of());
        if (required.isEmpty()) return;
        for (String typeCode : required) {
            if (!hasActiveCertification(userId, typeCode)) {
                throw RestException.conflict(
                        "User " + userId + " is missing active certification '" + typeCode
                                + "' required for role '" + roleCode + "'");
            }
        }
    }

    public boolean hasActiveCertification(UUID userId, String typeCode) {
        LocalDate today = LocalDate.now();
        return repo.findAllByUserId(userId).stream()
                .filter(c -> typeCode.equals(c.getTypeCode()))
                .filter(c -> "ACTIVE".equals(c.getStatus()))
                .anyMatch(c -> c.getExpiresAt() == null || !c.getExpiresAt().isBefore(today));
    }
}
