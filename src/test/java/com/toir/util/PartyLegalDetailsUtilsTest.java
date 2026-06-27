package com.toir.util;

import com.toir.dto.common.BankAccountDto;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PartyLegalDetailsUtilsTest {

    @Test
    void normalizeTaxNumberPreservesLeadingZerosAndBranchSuffix() {
        assertThat(PartyLegalDetailsUtils.normalizeTaxNumber(" 0123456789 ")).isEqualTo("0123456789");
        assertThat(PartyLegalDetailsUtils.normalizeTaxNumber("123456789_1")).isEqualTo("123456789_1");
    }

    @Test
    void resolveBaseInnStripsNumericBranchSuffix() {
        assertThat(PartyLegalDetailsUtils.resolveBaseInn("123456789_1", null)).isEqualTo("123456789");
        assertThat(PartyLegalDetailsUtils.resolveBaseInn("0123456789", null)).isEqualTo("0123456789");
        assertThat(PartyLegalDetailsUtils.resolveBaseInn("123456789_1", "999999999")).isEqualTo("999999999");
    }

    @Test
    void normalizeBankAccountsKeepsMultipleAccountsAsStrings() {
        List<BankAccountDto> result = PartyLegalDetailsUtils.normalizeBankAccounts(List.of(
                new BankAccountDto("NBU", "20208000123456789", "00401"),
                new BankAccountDto("Kapitalbank", "00208000987654321", "01158")
        ));

        assertThat(result).hasSize(2);
        assertThat(result.get(0).bankAccount()).isEqualTo("20208000123456789");
        assertThat(result.get(1).bankAccount()).isEqualTo("00208000987654321");
    }

    @Test
    void normalizeBankAccountsSkipsCompletelyEmptyEntries() {
        List<BankAccountDto> result = PartyLegalDetailsUtils.normalizeBankAccounts(List.of(
                new BankAccountDto("NBU", "20208000123456789", "00401"),
                new BankAccountDto("  ", null, null)
        ));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).bankName()).isEqualTo("NBU");
    }
}
