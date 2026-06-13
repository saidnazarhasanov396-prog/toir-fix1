package com.toir.service.approval;

import com.toir.dto.approval.ApprovalAnalyticsDto;
import com.toir.entity.ApprovalRequest;
import com.toir.enums.ApprovalStatus;
import com.toir.repository.ApprovalRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ApprovalAnalyticsService {

    private final ApprovalRequestRepository requestRepository;

    @Transactional(readOnly = true)
    public ApprovalAnalyticsDto dashboard() {
        List<ApprovalRequest> approvals = requestRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc();
        long pending = approvals.stream().filter(request -> request.getStatus() == ApprovalStatus.PENDING).count();
        long expired = approvals.stream().filter(request -> request.getStatus() == ApprovalStatus.EXPIRED).count();
        long escalated = approvals.stream().filter(request -> request.getEscalatedAt() != null).count();

        List<ApprovalRequest> completed = approvals.stream()
                .filter(request -> request.getCompletedAt() != null && request.getCreatedAt() != null)
                .toList();
        double averageHours = completed.stream()
                .mapToDouble(request -> Duration.between(request.getCreatedAt(), request.getCompletedAt()).toMinutes() / 60.0)
                .average()
                .orElse(0.0);

        List<ApprovalRequest> slaMeasured = completed.stream()
                .filter(request -> request.getExpiresAt() != null)
                .toList();
        long withinSla = slaMeasured.stream()
                .filter(request -> !request.getCompletedAt().isAfter(request.getExpiresAt()))
                .count();
        double slaCompliance = slaMeasured.isEmpty() ? 100.0 : (withinSla * 100.0) / slaMeasured.size();

        Map<String, Long> byModule = approvals.stream()
                .collect(Collectors.groupingBy(this::moduleKey, LinkedHashMap::new, Collectors.counting()));

        return new ApprovalAnalyticsDto(pending, expired, escalated, averageHours, slaCompliance, byModule);
    }

    private String moduleKey(ApprovalRequest request) {
        if (request.getTargetType() != null) {
            return request.getTargetType().name();
        }
        return request.getDocumentType() == null ? "UNKNOWN" : request.getDocumentType();
    }
}
