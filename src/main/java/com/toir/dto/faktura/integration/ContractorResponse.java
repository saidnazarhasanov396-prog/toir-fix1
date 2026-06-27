package com.toir.dto.faktura.integration;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ContractorResponse {
    @JsonProperty("name")
    private String name;
    @JsonProperty("inn")
    private String inn;
    @JsonProperty("branchCode")
    private String branchCode;
    @JsonProperty("branchName")
    private String branchName;
}
