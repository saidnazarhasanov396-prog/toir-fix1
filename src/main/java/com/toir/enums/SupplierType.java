package com.toir.enums;

public enum SupplierType {
    EQUIPMENT,
    SPARE_PART,
    BOTH;

    public boolean supports(SupplierType requiredType) {
        if (requiredType == null) {
            return true;
        }
        return this == BOTH || this == requiredType;
    }
}
