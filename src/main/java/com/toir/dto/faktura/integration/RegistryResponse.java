package com.toir.dto.faktura.integration;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RegistryResponse {
    @JsonProperty("id")
    private String id;
    @JsonProperty("fileName")
    private String fileName;
    @JsonProperty("uniqueId")
    private String uniqueId;
}
