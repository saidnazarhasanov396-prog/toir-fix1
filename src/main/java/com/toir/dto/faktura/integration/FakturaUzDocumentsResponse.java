package com.toir.dto.faktura.integration;

import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FakturaUzDocumentsResponse {
    private List<FakturaUzDocumentResponse> documents;
    private Double totalPrice;
    private Long totalCount;
}
