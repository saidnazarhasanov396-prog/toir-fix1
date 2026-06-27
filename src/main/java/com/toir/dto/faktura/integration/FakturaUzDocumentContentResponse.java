package com.toir.dto.faktura.integration;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FakturaUzDocumentContentResponse {
    @JsonProperty("UniqueId")
    private String uniqueId;
    @JsonProperty("RoamingUid")
    private String roamingUid;
    @JsonProperty("Content")
    private JsonNode content;
}
