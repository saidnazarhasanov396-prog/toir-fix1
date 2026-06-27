package com.toir.util;

import com.toir.dto.common.BankAccountDto;
import com.toir.entity.common.BankAccount;
import java.util.ArrayList;
import java.util.List;

public final class PartyLegalDetailsUtils {

    private PartyLegalDetailsUtils() {
    }

    /**
     * INN/STIR is always stored as plain text — leading zeros and branch suffixes are preserved.
     */
    public static String normalizeTaxNumber(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public static String resolveBaseInn(String taxNumber, String explicitBaseInn) {
        String normalizedExplicit = normalizeTaxNumber(explicitBaseInn);
        if (normalizedExplicit != null) {
            return normalizedExplicit;
        }
        String normalizedTaxNumber = normalizeTaxNumber(taxNumber);
        if (normalizedTaxNumber == null) {
            return null;
        }
        int underscoreIdx = normalizedTaxNumber.lastIndexOf('_');
        if (underscoreIdx > 0) {
            String suffix = normalizedTaxNumber.substring(underscoreIdx + 1);
            if (suffix.matches("\\d+")) {
                return normalizedTaxNumber.substring(0, underscoreIdx);
            }
        }
        return normalizedTaxNumber;
    }

    public static List<BankAccountDto> normalizeBankAccounts(List<BankAccountDto> accounts) {
        if (accounts == null || accounts.isEmpty()) {
            return List.of();
        }
        List<BankAccountDto> normalized = new ArrayList<>();
        for (BankAccountDto account : accounts) {
            if (account == null) {
                continue;
            }
            String bankName = trimToNull(account.bankName());
            String bankAccount = trimToNull(account.bankAccount());
            String mfo = trimToNull(account.mfo());
            if (bankName == null && bankAccount == null && mfo == null) {
                continue;
            }
            normalized.add(new BankAccountDto(bankName, bankAccount, mfo));
        }
        return List.copyOf(normalized);
    }

    public static List<BankAccount> toBankAccounts(List<BankAccountDto> accounts) {
        return normalizeBankAccounts(accounts).stream()
                .map(dto -> new BankAccount(dto.bankName(), dto.bankAccount(), dto.mfo()))
                .toList();
    }

    public static List<BankAccountDto> toBankAccountDtos(List<BankAccount> accounts) {
        if (accounts == null || accounts.isEmpty()) {
            return List.of();
        }
        return accounts.stream()
                .map(account -> new BankAccountDto(
                        account.getBankName(),
                        account.getBankAccount(),
                        account.getMfo()))
                .toList();
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
