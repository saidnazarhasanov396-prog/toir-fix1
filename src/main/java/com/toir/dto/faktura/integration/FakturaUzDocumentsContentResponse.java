package com.toir.dto.faktura.integration;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FakturaUzDocumentsContentResponse {
    @JsonProperty("Success")
    private Boolean success;
    @JsonProperty("Documents")
    private List<FakturaUzDocumentContentResponse> documents;
}
