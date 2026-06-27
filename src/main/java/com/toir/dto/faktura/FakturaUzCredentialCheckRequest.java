package com.toir.dto.faktura;

import jakarta.validation.constraints.NotBlank;

public record FakturaUzCredentialCheckRequest(
        @NotBlank String login,
        @NotBlank String password,
        @NotBlank String clientId,
        @NotBlank String clientSecret,
        String expectedCompanyInn
) {
}
