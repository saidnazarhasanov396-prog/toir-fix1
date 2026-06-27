package com.toir.dto.faktura.integration;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class FakturaUzGetDocumentsContentRequest {
    private String companyInn;
    @Builder.Default
    private Boolean isDeserialized = true;
    @JsonProperty("DocumentUniqueIds")
    private List<String> documentUniqueIds;
    private String authToken;
}
