package com.toir.service.faktura;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.toir.dto.faktura.FakturaUzCredentialCheckRequest;
import com.toir.dto.faktura.FakturaUzCredentialCheckResponse;
import com.toir.dto.faktura.FakturaUzDocumentContentDto;
import com.toir.dto.faktura.FakturaUzDocumentDto;
import com.toir.dto.faktura.FakturaUzImportHistoryDto;
import com.toir.dto.faktura.FakturaUzImportRequest;
import com.toir.dto.faktura.FakturaUzImportResponse;
import com.toir.dto.faktura.FakturaUzType32ContentDto;
import com.toir.dto.faktura.FakturaUzType32PartDto;
import com.toir.dto.faktura.FakturaUzType32ServiceDto;
import com.toir.dto.faktura.integration.ContractorResponse;
import com.toir.dto.faktura.integration.FakturaUzAuthResponse;
import com.toir.dto.faktura.integration.FakturaUzDocumentContentResponse;
import com.toir.dto.faktura.integration.FakturaUzDocumentResponse;
import com.toir.dto.faktura.integration.FakturaUzDocumentsContentResponse;
import com.toir.dto.faktura.integration.FakturaUzDocumentsResponse;
import com.toir.dto.faktura.integration.FakturaUzGetDocumentsContentRequest;
import com.toir.dto.faktura.integration.FakturaUzGetDocumentsRequest;
import com.toir.dto.faktura.integration.FakturaUzUserDetailsResponse;
import com.toir.entity.IntegrationEndpoint;
import com.toir.entity.IntegrationSyncLog;
import com.toir.entity.faktura.FakturaUzDocument;
import com.toir.entity.faktura.FakturaUzDocumentContent;
import com.toir.entity.faktura.FakturaUzDocumentType32Content;
import com.toir.entity.faktura.FakturaUzDocumentType32Part;
import com.toir.entity.faktura.FakturaUzDocumentType32Service;
import com.toir.entity.faktura.FakturaUzImportHistory;
import com.toir.enums.FakturaUzDocumentType;
import com.toir.enums.IntegrationSyncStatus;
import com.toir.exception.RestException;
import com.toir.repository.IntegrationEndpointRepository;
import com.toir.repository.IntegrationSyncLogRepository;
import com.toir.repository.faktura.FakturaUzDocumentContentRepository;
import com.toir.repository.faktura.FakturaUzDocumentRepository;
import com.toir.repository.faktura.FakturaUzDocumentType32ContentRepository;
import com.toir.repository.faktura.FakturaUzDocumentType32PartRepository;
import com.toir.repository.faktura.FakturaUzDocumentType32ServiceRepository;
import com.toir.repository.faktura.FakturaUzImportHistoryRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class FakturaUzService {
    private static final int PAGE_LIMIT = 100;
    private static final int CONTENT_BATCH_SIZE = 50;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final FakturaUzClientService clientService;
    private final IntegrationEndpointRepository endpointRepository;
    private final IntegrationSyncLogRepository syncLogRepository;
    private final FakturaUzDocumentRepository documentRepository;
    private final FakturaUzDocumentContentRepository contentRepository;
    private final FakturaUzDocumentType32ContentRepository type32ContentRepository;
    private final FakturaUzDocumentType32ServiceRepository type32ServiceRepository;
    private final FakturaUzDocumentType32PartRepository type32PartRepository;
    private final FakturaUzImportHistoryRepository historyRepository;
    private final ObjectMapper objectMapper;

    public FakturaUzCredentialCheckResponse checkCredentials(FakturaUzCredentialCheckRequest request) {
        FakturaUzAuthResponse auth = clientService.getAuthToken(
                request.login(),
                request.password(),
                request.clientId(),
                request.clientSecret()
        );
        FakturaUzUserDetailsResponse details = clientService.getUserDetails(auth.getAccessToken());
        FakturaUzUserDetailsResponse.Company company = details.getCompanies().getFirst();
        if (request.expectedCompanyInn() != null && !request.expectedCompanyInn().isBlank()
                && !request.expectedCompanyInn().equals(company.getInn())) {
            throw RestException.badRequest("FakturaUz company INN mismatch. Expected "
                    + request.expectedCompanyInn() + ", got " + company.getInn());
        }
        return FakturaUzCredentialCheckResponse.ok(company.getInn(), company.getName());
    }

    @Transactional
    public FakturaUzImportResponse importDocuments(FakturaUzImportRequest request) {
        if (request.toDate().isBefore(request.fromDate())) {
            throw RestException.badRequest("toDate must be equal to or after fromDate");
        }
        IntegrationEndpoint endpoint = endpointRepository.findByIdAndIsDeletedFalse(request.endpointId())
                .orElseThrow(() -> RestException.notFound("Integration endpoint not found: " + request.endpointId()));
        assertFakturaEndpoint(endpoint);
        String companyInn = require(endpoint.getCompanyInn(), "Integration endpoint companyInn is required");
        String token = authenticate(endpoint).getAccessToken();

        IntegrationSyncLog syncLog = new IntegrationSyncLog();
        syncLog.setEndpointId(endpoint.getId());
        syncLog.setDirection("INBOUND");
        syncLog.setModule("FAKTURA_UZ_" + request.type().name());
        syncLog.setStartedAt(Instant.now());
        syncLog.setStatus(IntegrationSyncStatus.RUNNING);
        syncLog = syncLogRepository.save(syncLog);

        FakturaUzImportHistory history = new FakturaUzImportHistory();
        history.setEndpointId(endpoint.getId());
        history.setDocumentType(request.type());
        history.setImportedAt(LocalDateTime.now());
        history.setImportRequestDateFrom(request.fromDate());
        history.setImportRequestDateTo(request.toDate());

        long totalCountInRequest = 0;
        long savedCount = 0;
        long updatedCount = 0;
        long failedCount = 0;
        int skip = 0;
        long totalCount = Long.MAX_VALUE;

        try {
            while (skip < totalCount) {
                FakturaUzGetDocumentsRequest fetchRequest = FakturaUzGetDocumentsRequest.builder()
                        .organizationInn(companyInn)
                        .limit(PAGE_LIMIT)
                        .skip(skip)
                        .fromDateTime(request.fromDate().atStartOfDay())
                        .toDateTime(request.toDate().atTime(LocalTime.MAX))
                        .types(List.of(request.type().getId()))
                        .authToken(token)
                        .build();
                FakturaUzDocumentsResponse response = clientService.getDocuments(fetchRequest);
                List<FakturaUzDocumentResponse> documents = response != null ? response.getDocuments() : null;
                if (documents == null || documents.isEmpty()) {
                    break;
                }
                totalCount = response.getTotalCount() != null ? response.getTotalCount() : documents.size();
                totalCountInRequest = totalCount;

                List<String> uniqueIds = new ArrayList<>(documents.size());
                for (FakturaUzDocumentResponse doc : documents) {
                    try {
                        boolean isNew = upsertDocument(doc, endpoint.getId(), companyInn, request.type().getId());
                        if (isNew) {
                            savedCount++;
                        } else {
                            updatedCount++;
                        }
                        if (doc.getUniqueId() != null && !doc.getUniqueId().isBlank()) {
                            uniqueIds.add(doc.getUniqueId());
                        }
                    } catch (Exception e) {
                        failedCount++;
                        log.warn("Failed to upsert FakturaUz document. uniqueId={}, type={}", doc.getUniqueId(), request.type(), e);
                    }
                }
                fetchAndSaveContent(companyInn, token, request.type(), uniqueIds);
                skip += PAGE_LIMIT;
                if (documents.size() < PAGE_LIMIT) {
                    break;
                }
            }
            history.setDescription(failedCount == 0
                    ? "Success: " + request.type().getCode() + " / " + request.type().getDescription()
                    : "Completed with errors (" + failedCount + "): " + request.type().getCode() + " / " + request.type().getDescription());
            endpoint.setLastSyncStatus(IntegrationSyncStatus.SUCCESS);
            endpoint.setLastError(null);
            syncLog.setStatus(IntegrationSyncStatus.SUCCESS);
            syncLog.setErrorMessage(null);
        } catch (Exception e) {
            endpoint.setLastSyncStatus(IntegrationSyncStatus.FAILED);
            endpoint.setLastError(e.getMessage());
            history.setDescription("Error: " + e.getMessage());
            syncLog.setStatus(IntegrationSyncStatus.FAILED);
            syncLog.setErrorMessage(e.getMessage());
        } finally {
            syncLog.setFinishedAt(Instant.now());
            syncLog.setRecordsReceived(safeInt(savedCount + updatedCount));
            endpoint.setLastSyncAt(Instant.now());
            history.setTotalDataCountInRequest(totalCountInRequest);
            history.setTotalSavedDataCount(savedCount);
            history.setTotalUpdatedDataCount(updatedCount);
            history.setTotalFailedDataCount(failedCount);
            endpointRepository.save(endpoint);
            historyRepository.save(history);
            syncLogRepository.save(syncLog);
        }

        return new FakturaUzImportResponse(
                history.getId(),
                endpoint.getId(),
                request.type(),
                totalCountInRequest,
                savedCount,
                updatedCount,
                failedCount,
                history.getDescription()
        );
    }

    @Transactional(readOnly = true)
    public Page<FakturaUzDocumentDto> getDocuments(UUID endpointId,
                                                   LocalDate fromDate,
                                                   LocalDate toDate,
                                                   String query,
                                                   Integer type,
                                                   Pageable pageable) {
        Long fromMillis = fromDate == null ? null : fromDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        Long toMillis = toDate == null ? null : toDate.atTime(LocalTime.MAX).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        return documentRepository.search(endpointId, type, fromMillis, toMillis, query, pageable)
                .map(this::toDocumentDto);
    }

    @Transactional(readOnly = true)
    public FakturaUzDocumentContentDto getDocumentContent(Integer type, String uniqueId) {
        if (type == null) {
            throw RestException.badRequest("Document type is required");
        }
        if (uniqueId == null || uniqueId.isBlank()) {
            throw RestException.badRequest("uniqueId is required");
        }
        documentRepository.findByUniqueIdAndIsDeletedFalse(uniqueId)
                .orElseThrow(() -> RestException.notFound("FakturaUz document not found: " + uniqueId));
        FakturaUzDocumentType resolvedType = FakturaUzDocumentType.getById(type);
        JsonNode raw = contentRepository.findByDocumentUniqueIdAndIsDeletedFalse(uniqueId)
                .map(FakturaUzDocumentContent::getRawContentJson)
                .map(this::readJson)
                .orElse(null);
        FakturaUzType32ContentDto type32 = null;
        List<FakturaUzType32ServiceDto> services = List.of();
        List<FakturaUzType32PartDto> parts = List.of();
        if (type == FakturaUzDocumentType.CONTRACT_ROAMING.getId()) {
            type32 = type32ContentRepository.findByDocumentUniqueIdAndIsDeletedFalse(uniqueId)
                    .map(FakturaUzType32ContentDto::from)
                    .orElse(null);
            services = type32ServiceRepository.findAllByDocumentUniqueIdAndIsDeletedFalseOrderByNumberAsc(uniqueId)
                    .stream()
                    .map(FakturaUzType32ServiceDto::from)
                    .toList();
            parts = type32PartRepository.findAllByDocumentUniqueIdAndIsDeletedFalseOrderByNumberAsc(uniqueId)
                    .stream()
                    .map(FakturaUzType32PartDto::from)
                    .toList();
        }
        return new FakturaUzDocumentContentDto(
                uniqueId,
                type,
                resolvedType.getCode(),
                resolvedType.getDescription(),
                raw,
                type32,
                services,
                parts
        );
    }

    @Transactional(readOnly = true)
    public Page<FakturaUzImportHistoryDto> getImportHistory(UUID endpointId, Pageable pageable) {
        Page<FakturaUzImportHistory> page = endpointId == null
                ? historyRepository.findAllByIsDeletedFalseOrderByImportedAtDesc(pageable)
                : historyRepository.findAllByEndpointIdAndIsDeletedFalseOrderByImportedAtDesc(endpointId, pageable);
        return page.map(FakturaUzImportHistoryDto::from);
    }

    private FakturaUzAuthResponse authenticate(IntegrationEndpoint endpoint) {
        return clientService.getAuthToken(
                require(endpoint.getUsername(), "Integration endpoint username is required"),
                require(endpoint.getPassword(), "Integration endpoint password is required"),
                require(endpoint.getClientId(), "Integration endpoint clientId is required"),
                require(endpoint.getClientSecret(), "Integration endpoint clientSecret is required")
        );
    }

    private void fetchAndSaveContent(String companyInn, String token, FakturaUzDocumentType type, List<String> uniqueIds) {
        if (uniqueIds == null || uniqueIds.isEmpty()) {
            return;
        }
        for (int i = 0; i < uniqueIds.size(); i += CONTENT_BATCH_SIZE) {
            List<String> batch = uniqueIds.subList(i, Math.min(i + CONTENT_BATCH_SIZE, uniqueIds.size()));
            FakturaUzDocumentsContentResponse response = clientService.getDocumentsContent(
                    FakturaUzGetDocumentsContentRequest.builder()
                            .companyInn(companyInn)
                            .isDeserialized(true)
                            .documentUniqueIds(batch)
                            .authToken(token)
                            .build()
            );
            if (response == null || response.getDocuments() == null) {
                continue;
            }
            for (FakturaUzDocumentContentResponse contentDoc : response.getDocuments()) {
                if (contentDoc.getContent() == null || contentDoc.getUniqueId() == null) {
                    continue;
                }
                upsertRawContent(contentDoc, type.getId());
                if (type == FakturaUzDocumentType.CONTRACT_ROAMING) {
                    upsertType32Content(contentDoc.getUniqueId(), contentDoc.getRoamingUid(), contentDoc.getContent());
                }
            }
        }
    }

    private boolean upsertDocument(FakturaUzDocumentResponse doc, UUID endpointId, String companyInn, int type) {
        if (doc == null || doc.getUniqueId() == null || doc.getUniqueId().isBlank()) {
            return false;
        }
        boolean isNew = false;
        FakturaUzDocument entity = documentRepository.findByUniqueIdAndIsDeletedFalse(doc.getUniqueId())
                .orElseGet(FakturaUzDocument::new);
        if (entity.getId() == null) {
            isNew = true;
        }
        applyDocumentFields(entity, doc, endpointId, companyInn, type);
        try {
            documentRepository.save(entity);
        } catch (DataIntegrityViolationException e) {
            FakturaUzDocument existing = documentRepository.findByUniqueIdAndIsDeletedFalse(doc.getUniqueId())
                    .orElseThrow(() -> e);
            applyDocumentFields(existing, doc, endpointId, companyInn, type);
            documentRepository.save(existing);
            return false;
        }
        return isNew;
    }

    private void applyDocumentFields(FakturaUzDocument entity,
                                     FakturaUzDocumentResponse doc,
                                     UUID endpointId,
                                     String companyInn,
                                     int type) {
        entity.setEndpointId(endpointId);
        entity.setUniqueId(doc.getUniqueId());
        entity.setRoamingUid(doc.getRoamingUid());
        entity.setType(type);
        entity.setTitle(doc.getTitle());
        entity.setFileName(doc.getFileName());
        entity.setTotalPrice(parseDecimal(doc.getTotalPrice()));
        entity.setContract(doc.getContract());
        entity.setCreatedDateTime(doc.getCreatedDateTime());
        entity.setUpdatedDateTime(doc.getUpdatedDateTime());
        entity.setIsNew(doc.getIsNew());
        entity.setStatus(doc.getStatus());
        entity.setOrganizationInn(companyInn);
        applyContractor(entity, doc.getContractor(), doc.getOwnerMember(), doc.getContractorMember());
    }

    private void applyContractor(FakturaUzDocument entity,
                                 ContractorResponse contractor,
                                 ContractorResponse ownerMember,
                                 ContractorResponse contractorMember) {
        if (contractor != null) {
            entity.setContractorInn(contractor.getInn());
            entity.setContractorName(contractor.getName());
        }
        if (ownerMember != null) {
            entity.setOwnerInn(ownerMember.getInn());
            entity.setOwnerName(ownerMember.getName());
        }
        if (contractorMember != null) {
            entity.setContractorMemberInn(contractorMember.getInn());
            entity.setContractorMemberName(contractorMember.getName());
        }
    }

    private void upsertRawContent(FakturaUzDocumentContentResponse contentDoc, int type) {
        FakturaUzDocumentContent entity = contentRepository.findByDocumentUniqueIdAndIsDeletedFalse(contentDoc.getUniqueId())
                .orElseGet(FakturaUzDocumentContent::new);
        entity.setDocumentUniqueId(contentDoc.getUniqueId());
        entity.setRoamingUid(contentDoc.getRoamingUid());
        entity.setType(type);
        entity.setRawContentJson(writeJson(contentDoc.getContent()));
        contentRepository.save(entity);
    }

    private void upsertType32Content(String uniqueId, String roamingUid, JsonNode content) {
        FakturaUzDocumentType32Content entity = type32ContentRepository.findByDocumentUniqueIdAndIsDeletedFalse(uniqueId)
                .orElseGet(FakturaUzDocumentType32Content::new);
        entity.setDocumentUniqueId(uniqueId);
        entity.setRoamingUid(roamingUid);
        entity.setHasVat(text(content, "hasVat"));

        JsonNode owner = content.get("owner");
        if (owner != null && owner.isObject()) {
            entity.setOwnerInn(text(owner, "inn"));
            entity.setOwnerName(text(owner, "name"));
            entity.setOwnerAccount(text(owner, "account"));
            entity.setOwnerMfo(text(owner, "mfo"));
            entity.setOwnerBank(text(owner, "bank"));
            entity.setOwnerAddress(text(owner, "address"));
            entity.setOwnerPhone(text(owner, "phone"));
        }
        JsonNode clients = content.get("clients");
        if (clients != null && clients.isArray() && !clients.isEmpty()) {
            JsonNode client = clients.get(0);
            entity.setClientInn(text(client, "inn"));
            entity.setClientName(text(client, "name"));
            entity.setClientAccount(text(client, "account"));
            entity.setClientMfo(text(client, "mfo"));
            entity.setClientBank(text(client, "bank"));
            entity.setClientAddress(text(client, "address"));
            entity.setClientPhone(text(client, "phone"));
        }

        entity.setContractName(text(content, "contractName"));
        entity.setContractorInn(text(content, "contractorInn"));
        entity.setContractNumber(text(content, "contractNumber"));
        entity.setContractDate(parseDate(text(content, "contractDate")));
        entity.setContractExpireDate(parseDate(text(content, "contractExpireDate")));
        entity.setContractPlace(text(content, "contractPlace"));
        entity.setInvoiceServicesDeliveryCostTotal(parseDecimal(nodeValue(content, "invoiceServicesDeliveryCostTotal")));
        entity.setInvoiceServicesVatAmountTotal(parseDecimal(nodeValue(content, "invoiceServicesVatAmountTotal")));
        entity.setInvoiceServicesTotalPrice(parseDecimal(nodeValue(content, "invoiceServicesTotalPrice")));
        entity.setInvoiceServicesTotalPriceInWords(text(content, "invoiceServicesTotalPriceInWords"));
        entity.setIsNewIdentity(content.has("isNewIdentity") && content.get("isNewIdentity").asBoolean());
        type32ContentRepository.save(entity);
        saveType32Services(uniqueId, content.get("services"));
        saveType32Parts(uniqueId, content.get("parts"));
    }

    private void saveType32Services(String uniqueId, JsonNode servicesNode) {
        List<FakturaUzDocumentType32Service> existingServices =
                type32ServiceRepository.findAllByDocumentUniqueIdAndIsDeletedFalseOrderByNumberAsc(uniqueId);
        existingServices.forEach(service -> service.setDeleted(true));
        type32ServiceRepository.saveAll(existingServices);
        if (servicesNode == null || !servicesNode.isArray()) {
            return;
        }
        List<FakturaUzDocumentType32Service> services = new ArrayList<>();
        for (JsonNode item : servicesNode) {
            FakturaUzDocumentType32Service service = new FakturaUzDocumentType32Service();
            service.setDocumentUniqueId(uniqueId);
            service.setPricePerItem(parseDecimal(nodeValue(item, "pricePerItem")));
            service.setPrice(parseDecimal(nodeValue(item, "price")));
            service.setSumma(parseDecimal(nodeValue(item, "summa")));
            service.setDeliveryCost(parseDecimal(nodeValue(item, "deliveryCost")));
            service.setVatRate(item.hasNonNull("vatRate") ? item.get("vatRate").asDouble() : null);
            service.setVatAmount(parseDecimal(nodeValue(item, "vatAmount")));
            service.setVatRateDisplay(text(item, "vatRateDisplay"));
            service.setVatAmountDisplay(text(item, "vatAmountDisplay"));
            service.setDeliveryCostWithVat(parseDecimal(nodeValue(item, "deliveryCostWithVat")));
            service.setDeliveryCostWithVatDisplay(text(item, "deliveryCostWithVatDisplay"));
            service.setTaxRate(text(item, "taxRate"));
            service.setTaxAmount(text(item, "taxAmount"));
            service.setDeliveryCostWithTaxes(parseDecimal(nodeValue(item, "deliveryCostWithTaxes")));
            service.setDeliveryCostWithTaxesDisplay(text(item, "deliveryCostWithTaxesDisplay"));
            JsonNode catalog = item.get("catalog");
            if (catalog != null && catalog.isObject()) {
                service.setCatalogCode(text(catalog, "code"));
                service.setCatalogName(text(catalog, "name"));
                service.setCatalogPackageNames(text(catalog, "packageNames"));
                service.setCatalogTitle(text(catalog, "title"));
            }
            service.setBarcode(text(item, "barcode"));
            service.setNumber(text(item, "number"));
            service.setTitle(text(item, "title"));
            service.setMeasurement(text(item, "measurement"));
            service.setMeasurementCode(text(item, "measurementCode"));
            service.setQuantity(item.hasNonNull("quantity") ? item.get("quantity").asDouble() : null);
            services.add(service);
        }
        if (!services.isEmpty()) {
            type32ServiceRepository.saveAll(services);
        }
    }

    private void saveType32Parts(String uniqueId, JsonNode partsNode) {
        List<FakturaUzDocumentType32Part> existingParts =
                type32PartRepository.findAllByDocumentUniqueIdAndIsDeletedFalseOrderByNumberAsc(uniqueId);
        existingParts.forEach(part -> part.setDeleted(true));
        type32PartRepository.saveAll(existingParts);
        if (partsNode == null || !partsNode.isArray()) {
            return;
        }
        List<FakturaUzDocumentType32Part> parts = new ArrayList<>();
        for (JsonNode item : partsNode) {
            FakturaUzDocumentType32Part part = new FakturaUzDocumentType32Part();
            part.setDocumentUniqueId(uniqueId);
            part.setNumber(text(item, "number"));
            part.setTitle(text(item, "title"));
            part.setBody(text(item, "body"));
            parts.add(part);
        }
        if (!parts.isEmpty()) {
            type32PartRepository.saveAll(parts);
        }
    }

    private FakturaUzDocumentDto toDocumentDto(FakturaUzDocument document) {
        FakturaUzDocumentType type = FakturaUzDocumentType.tryById(document.getType());
        return FakturaUzDocumentDto.from(
                document,
                type != null ? type.getCode() : null,
                type != null ? type.getDescription() : null
        );
    }

    private void assertFakturaEndpoint(IntegrationEndpoint endpoint) {
        if (!"FAKTURA_UZ".equalsIgnoreCase(endpoint.getSystem())) {
            throw RestException.badRequest("Integration endpoint system must be FAKTURA_UZ");
        }
        if (!endpoint.isActive()) {
            throw RestException.badRequest("Integration endpoint is inactive: " + endpoint.getId());
        }
    }

    private String require(String value, String message) {
        if (value == null || value.isBlank()) {
            throw RestException.badRequest(message);
        }
        return value;
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node != null ? node.get(field) : null;
        if (value == null || value.isNull()) {
            return null;
        }
        String text = value.asText();
        return text == null || text.isBlank() ? null : text;
    }

    private String nodeValue(JsonNode node, String field) {
        JsonNode value = node != null ? node.get(field) : null;
        return value == null || value.isNull() ? null : value.asText();
    }

    private LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim(), DATE_FORMATTER);
        } catch (Exception ignored) {
            return null;
        }
    }

    private BigDecimal parseDecimal(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(raw.trim().replaceAll("\\s+", "").replace(",", "."));
        } catch (Exception ignored) {
            return null;
        }
    }

    private String writeJson(JsonNode node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (Exception e) {
            throw RestException.badRequest("Failed to serialize FakturaUz content: " + e.getMessage());
        }
    }

    private JsonNode readJson(String raw) {
        try {
            return raw == null || raw.isBlank() ? null : objectMapper.readTree(raw);
        } catch (Exception e) {
            return null;
        }
    }

    private Integer safeInt(long value) {
        return value > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) value;
    }
}
