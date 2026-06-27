package com.toir.dto.faktura;

import com.toir.entity.faktura.FakturaUzDocumentType32Part;

public record FakturaUzType32PartDto(
        String number,
        String title,
        String body
) {
    public static FakturaUzType32PartDto from(FakturaUzDocumentType32Part e) {
        return new FakturaUzType32PartDto(e.getNumber(), e.getTitle(), e.getBody());
    }
}
