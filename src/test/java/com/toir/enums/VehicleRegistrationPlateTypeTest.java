package com.toir.enums;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class VehicleRegistrationPlateTypeTest {

    @Test
    void exposesReducedFrontendTemplateFamiliesOnly() {
        assertThat(Arrays.stream(VehicleRegistrationPlateType.values()).map(Enum::name))
                .containsExactly(
                        "INDIVIDUAL",
                        "LEGAL_ENTITY",
                        "INDIVIDUAL_ECO",
                        "LEGAL_ENTITY_ECO",
                        "GOVERNMENT",
                        "EMERGENCY",
                        "DIPLOMATIC",
                        "INTERNATIONAL_ORG",
                        "FOREIGN_PERSON",
                        "FOREIGN_ORG",
                        "MOTORCYCLE",
                        "TRAILER",
                        "TRANSIT",
                        "UNKNOWN"
                )
                .doesNotContain(
                        "LEGAL_ENTITY_TWO_LINE",
                        "DIPLOMATIC_CMD",
                        "TEMPORARY_TRANSIT",
                        "OTHER"
                );
    }
}
