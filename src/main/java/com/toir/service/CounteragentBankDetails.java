package com.toir.service;

import com.toir.dto.counteragent.CounteragentBankDetailRequest;
import com.toir.dto.counteragent.CounteragentRequest;
import com.toir.entity.Counteragent;
import com.toir.entity.CounteragentBankDetail;
import com.toir.exception.RestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

final class CounteragentBankDetails {

    private static final Logger log = LoggerFactory.getLogger(CounteragentBankDetails.class);
    private static final int MAX_BANK_DETAILS = 10;
    private static final Comparator<CounteragentBankDetail> STORED_ORDER =
            Comparator.comparingInt(CounteragentBankDetail::getDisplayOrder)
                    .thenComparing(detail -> detail.getId() == null ? new UUID(0, 0) : detail.getId());

    private CounteragentBankDetails() {
    }

    static void apply(Counteragent counteragent, CounteragentRequest request) {
        boolean arrayProvided = request.bankDetails() != null;
        if (arrayProvided) {
            validate(request.bankDetails(), true);
        }
        List<CounteragentBankDetailRequest> requested = requestedDetails(request);
        if (!arrayProvided) {
            validate(requested, false);
        }
        reconcile(counteragent, requested);
        syncLegacyMirror(counteragent);
    }

    private static List<CounteragentBankDetailRequest> requestedDetails(CounteragentRequest request) {
        if (request.bankDetails() == null) {
            if (!hasLegacyValues(request)) {
                return List.of();
            }
            log.warn("Deprecated counteragent bank fields used; migrate client to bankDetails");
            if (isPartialLegacy(request)) {
                log.warn("Partial deprecated counteragent bank fields preserved for remediation");
            }
            return List.of(new CounteragentBankDetailRequest(
                    null,
                    trimToEmpty(request.bankName()),
                    trimToEmpty(request.bankAccount()),
                    trimToEmpty(request.mfo()),
                    true
            ));
        }

        if (hasLegacyValues(request)) {
            CounteragentBankDetailRequest representative = request.bankDetails().stream()
                    .filter(Objects::nonNull)
                    .filter(CounteragentBankDetailRequest::isPrimary)
                    .findFirst()
                    .orElseGet(() -> request.bankDetails().stream().filter(Objects::nonNull).findFirst().orElse(null));
            if (!matchesLegacy(representative, request)) {
                throw RestException.badRequest("Legacy bank fields conflict with bankDetails");
            }
            log.warn("Deprecated counteragent bank fields used together with bankDetails");
        }

        return request.bankDetails();
    }

    private static void validate(
            List<CounteragentBankDetailRequest> requested,
            boolean requireCompleteFields
    ) {
        if (requested.size() > MAX_BANK_DETAILS) {
            throw RestException.badRequest("Counteragent can have at most 10 bank details");
        }

        long primaryCount = requested.stream()
                .filter(Objects::nonNull)
                .filter(CounteragentBankDetailRequest::isPrimary)
                .count();
        if (primaryCount > 1) {
            throw RestException.badRequest("Only one bank detail can be primary");
        }

        for (CounteragentBankDetailRequest detail : requested) {
            if (detail == null || (requireCompleteFields
                    && (trimToNull(detail.bankName()) == null
                    || trimToNull(detail.bankAccount()) == null
                    || trimToNull(detail.mfo()) == null))) {
                throw RestException.badRequest("Bank detail fields are required");
            }
        }
    }

    private static void reconcile(
            Counteragent counteragent,
            List<CounteragentBankDetailRequest> requested
    ) {
        List<CounteragentBankDetail> current = counteragent.getBankDetails();
        if (current == null) {
            current = new ArrayList<>();
            counteragent.setBankDetails(current);
        }

        Map<UUID, CounteragentBankDetail> existingById = new HashMap<>();
        for (CounteragentBankDetail detail : current) {
            if (detail.getId() != null) {
                existingById.put(detail.getId(), detail);
            }
        }

        List<CounteragentBankDetail> next = new ArrayList<>(requested.size());
        for (int index = 0; index < requested.size(); index++) {
            CounteragentBankDetailRequest requestedDetail = requested.get(index);
            CounteragentBankDetail detail;
            if (requestedDetail.id() == null) {
                detail = new CounteragentBankDetail();
                detail.setCounteragent(counteragent);
            } else {
                detail = existingById.get(requestedDetail.id());
                if (detail == null) {
                    throw RestException.badRequest(
                            "Bank detail " + requestedDetail.id() + " does not belong to counteragent"
                    );
                }
            }
            detail.setBankName(requestedDetail.bankName().trim());
            detail.setBankAccount(requestedDetail.bankAccount().trim());
            detail.setMfo(requestedDetail.mfo().trim());
            detail.setPrimary(requestedDetail.isPrimary());
            detail.setDisplayOrder(index);
            next.add(detail);
        }

        current.clear();
        current.addAll(next);
    }

    private static void syncLegacyMirror(Counteragent counteragent) {
        CounteragentBankDetail effectivePrimary = counteragent.getBankDetails().stream()
                .sorted(STORED_ORDER)
                .filter(CounteragentBankDetail::isPrimary)
                .findFirst()
                .orElseGet(() -> counteragent.getBankDetails().stream().sorted(STORED_ORDER).findFirst().orElse(null));

        counteragent.setBankName(effectivePrimary == null ? null : trimToNull(effectivePrimary.getBankName()));
        counteragent.setBankAccount(
                effectivePrimary == null ? null : trimToNull(effectivePrimary.getBankAccount())
        );
        counteragent.setMfo(effectivePrimary == null ? null : trimToNull(effectivePrimary.getMfo()));
    }

    private static boolean matchesLegacy(
            CounteragentBankDetailRequest representative,
            CounteragentRequest request
    ) {
        if (representative == null) {
            return false;
        }
        return Objects.equals(trimToNull(representative.bankName()), trimToNull(request.bankName()))
                && Objects.equals(
                        trimToNull(representative.bankAccount()),
                        trimToNull(request.bankAccount())
                )
                && Objects.equals(trimToNull(representative.mfo()), trimToNull(request.mfo()));
    }

    private static boolean hasLegacyValues(CounteragentRequest request) {
        return trimToNull(request.bankName()) != null
                || trimToNull(request.bankAccount()) != null
                || trimToNull(request.mfo()) != null;
    }

    private static boolean isPartialLegacy(CounteragentRequest request) {
        int filled = 0;
        if (trimToNull(request.bankName()) != null) filled++;
        if (trimToNull(request.bankAccount()) != null) filled++;
        if (trimToNull(request.mfo()) != null) filled++;
        return filled > 0 && filled < 3;
    }

    private static String trimToEmpty(String value) {
        String normalized = trimToNull(value);
        return normalized == null ? "" : normalized;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
