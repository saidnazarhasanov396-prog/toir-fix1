package com.toir.enums;

import com.toir.exception.RestException;
import lombok.Getter;

@Getter
public enum FakturaUzDocumentType {
    CONTRACT(1, "Contract", "Договор"),
    POWER_OF_ATTORNEY(20, "PowerOfAttorney", "Доверенность"),
    UNIVERSAL_INVOICE(28, "UniversalInvoice", "Счет-фактура (ПКМ №489)"),
    UNIVERSAL_ACT_INVOICE(29, "UniversalActInvoice", "Акт и счет-фактура (ПКМ №489)"),
    CONTRACT_ROAMING(32, "ContractRoaming", "Договор 2020");

    private final int id;
    private final String code;
    private final String description;

    FakturaUzDocumentType(int id, String code, String description) {
        this.id = id;
        this.code = code;
        this.description = description;
    }

    public static FakturaUzDocumentType getById(int id) {
        FakturaUzDocumentType type = tryById(id);
        if (type == null) {
            throw RestException.badRequest("FakturaUz document type id not supported: " + id);
        }
        return type;
    }

    public static FakturaUzDocumentType tryById(Integer id) {
        if (id == null) {
            return null;
        }
        for (FakturaUzDocumentType type : values()) {
            if (type.id == id) {
                return type;
            }
        }
        return null;
    }
}
