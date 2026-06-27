package com.toir.dto.faktura;

public record FakturaUzCredentialCheckResponse(
        boolean valid,
        String companyInn,
        String companyName,
        String error
) {
    public static FakturaUzCredentialCheckResponse ok(String companyInn, String companyName) {
        return new FakturaUzCredentialCheckResponse(true, companyInn, companyName, null);
    }
}
