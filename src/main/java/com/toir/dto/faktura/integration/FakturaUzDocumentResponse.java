package com.toir.dto.faktura.integration;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FakturaUzDocumentResponse {
    private String uniqueId;
    private String roamingUid;
    private String title;
    private String fileName;
    private String totalPrice;
    private String contract;
    private Long createdDateTime;
    private Long updatedDateTime;
    private Boolean isNew;
    private Boolean isAgreementApproved;
    private RegistryResponse registry;
    private Integer status;
    private ContractorResponse contractor;
    private ContractorResponse ownerMember;
    private ContractorResponse contractorMember;
}
