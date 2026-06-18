package com.toir.service;

import com.toir.dto.stockmovement.StockMovementDto;
import com.toir.dto.stockmovement.StockMovementDocumentDto;
import com.toir.dto.stockmovement.StockMovementFileDto;
import com.toir.dto.attachment.AttachmentGroupDto;
import com.toir.dto.stockmovement.StockMovementIssueRequest;
import com.toir.dto.stockmovement.StockMovementReceiptRequest;
import com.toir.dto.stockmovement.StockMovementRequest;
import com.toir.dto.warehouse.StockIssueCommand;
import com.toir.dto.warehouse.StockReceiptCommand;
import com.toir.dto.file.UploadFileResponse;
import com.toir.entity.StockMovementFile;
import com.toir.entity.StockMovement;
import com.toir.entity.UploadedFile;
import com.toir.entity.warehouse.Warehouse;
import com.toir.entity.warehouse.WarehouseStock;
import com.toir.enums.FileCategory;
import com.toir.enums.AttachmentTargetType;
import com.toir.enums.SparePartType;
import com.toir.enums.StockLedgerMovementType;
import com.toir.enums.StockMovementSourceType;
import com.toir.enums.StockMovementType;
import com.toir.exception.RestException;
import com.toir.repository.StockMovementFileRepository;
import com.toir.repository.ProcurementRequestRepository;
import com.toir.repository.SparePartRepository;
import com.toir.repository.StockMovementListRow;
import com.toir.repository.StockMovementRepository;
import com.toir.repository.UploadedFileRepository;
import com.toir.repository.WarehouseRepository;
import com.toir.repository.WarehouseStockRepository;
import com.toir.security.ScopeAccessService;
import com.toir.service.attachment.AttachmentGroupService;
import com.toir.service.file_management.FileService;
import com.toir.service.warehouse.ToirStockService;
import com.toir.util.AuditBuilderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockMovementServiceTest {

    @Mock
    StockMovementRepository repository;

    @Mock
    WarehouseStockRepository stockRepository;

    @Mock
    ProcurementRequestRepository procurementRequestRepository;

    @Mock
    SparePartRepository sparePartRepository;

    @Mock
    AuditBuilderService auditBuilderService;

    @Mock
    WarehouseRepository warehouseRepository;

    @Mock
    ScopeAccessService scopeAccessService;

    @Mock
    LowStockRecommendationService lowStockRecommendationService;

    @Mock
    ToirStockService toirStockService;

    @Mock
    FileService fileService;

    @Mock
    UploadedFileRepository uploadedFileRepository;

    @Mock
    StockMovementFileRepository stockMovementFileRepository;

    @Mock
    AttachmentGroupService attachmentGroupService;

    @InjectMocks
    StockMovementService service;

    @BeforeEach
    void setUp() {
        lenient().when(warehouseRepository.findByIdAndIsDeletedFalse(any())).thenAnswer(invocation -> {
            UUID warehouseId = invocation.getArgument(0);
            return Optional.of(warehouse(warehouseId));
        });
        lenient().when(scopeAccessService.isScopeAdmin()).thenReturn(true);
    }

    @Test
    void issueFailsWhenQuantityIsZeroOrNegative() {
        UUID warehouseId = UUID.randomUUID();
        UUID sparePartId = UUID.randomUUID();

        assertThatThrownBy(() -> service.create(request(warehouseId, sparePartId, StockMovementType.ISSUE, 0)))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("Quantity must be greater than 0");

        assertThatThrownBy(() -> service.create(request(warehouseId, sparePartId, StockMovementType.ISSUE, -1)))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("Quantity must be greater than 0");

        verifyNoInteractions(stockRepository, repository, sparePartRepository);
    }

    @Test
    void reservationFailsWhenQuantityIsZeroOrNegative() {
        UUID warehouseId = UUID.randomUUID();
        UUID sparePartId = UUID.randomUUID();

        assertThatThrownBy(() -> service.create(request(warehouseId, sparePartId, StockMovementType.RESERVATION, 0)))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("Quantity must be greater than 0");

        assertThatThrownBy(() -> service.create(request(warehouseId, sparePartId, StockMovementType.RESERVATION, -2)))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("Quantity must be greater than 0");

        verifyNoInteractions(stockRepository, repository, sparePartRepository);
    }

    @Test
    void directReservationMovementIsRejectedToKeepReservationEntityAsSourceOfTruth() {
        UUID warehouseId = UUID.randomUUID();
        UUID sparePartId = UUID.randomUUID();

        assertThatThrownBy(() -> service.create(request(warehouseId, sparePartId, StockMovementType.RESERVATION, 1)))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("/api/v1/reservations");

        verifyNoInteractions(stockRepository, repository, sparePartRepository);
    }

    @Test
    void adjustmentFailsWhenAdjustedQuantityIsLessThanReserved() {
        UUID warehouseId = UUID.randomUUID();
        UUID sparePartId = UUID.randomUUID();
        WarehouseStock stock = stock(warehouseId, sparePartId, 10, 5);

        when(stockRepository.findByWarehouseIdAndSparePartIdAndIsDeletedFalse(warehouseId, sparePartId))
                .thenReturn(Optional.of(stock));

        assertThatThrownBy(() -> service.create(request(warehouseId, sparePartId, StockMovementType.ADJUSTMENT, 4)))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("Cannot adjust quantity below reserved");

        assertThat(stock.getQuantity()).isEqualTo(10);
        verify(repository, never()).save(any(StockMovement.class));
    }

    @Test
    void adjustmentSucceedsWhenAdjustedQuantityIsGreaterThanOrEqualReserved() {
        UUID warehouseId = UUID.randomUUID();
        UUID sparePartId = UUID.randomUUID();
        WarehouseStock stock = stock(warehouseId, sparePartId, 10, 5);

        when(stockRepository.findByWarehouseIdAndSparePartIdAndIsDeletedFalse(warehouseId, sparePartId))
                .thenReturn(Optional.of(stock));

        when(repository.save(any(StockMovement.class)))
                .thenAnswer(invocation -> saveWithId(invocation.getArgument(0)));

        service.create(request(warehouseId, sparePartId, StockMovementType.ADJUSTMENT, 6));

        assertThat(stock.getQuantity()).isEqualTo(6);
        assertThat(stock.getQuantity()).isGreaterThanOrEqualTo(stock.getReservedQty());

        ArgumentCaptor<StockMovement> movementCaptor = ArgumentCaptor.forClass(StockMovement.class);
        verify(repository).save(movementCaptor.capture());
        assertThat(movementCaptor.getValue().getType()).isEqualTo(StockMovementType.ADJUSTMENT);
        assertThat(movementCaptor.getValue().getQuantity()).isEqualTo(6);

        ArgumentCaptor<StockIssueCommand> stockCommandCaptor = ArgumentCaptor.forClass(StockIssueCommand.class);
        verify(toirStockService).postDecrease(stockCommandCaptor.capture(), eq(StockLedgerMovementType.ADJUSTMENT_DEC));
        assertThat(stockCommandCaptor.getValue().quantity()).isEqualByComparingTo("4");
        assertThat(stockCommandCaptor.getValue().idempotencyKey()).startsWith("stock-movement-adjustment_dec:");
    }

    @Test
    void issueMutationNeverMakesQuantityNegative() {
        UUID warehouseId = UUID.randomUUID();
        UUID sparePartId = UUID.randomUUID();
        WarehouseStock stock = stock(warehouseId, sparePartId, 8, 3);

        when(stockRepository.findByWarehouseIdAndSparePartIdAndIsDeletedFalse(warehouseId, sparePartId))
                .thenReturn(Optional.of(stock));

        when(repository.save(any(StockMovement.class)))
                .thenAnswer(invocation -> saveWithId(invocation.getArgument(0)));

        service.create(request(warehouseId, sparePartId, StockMovementType.ISSUE, 5));

        assertThat(stock.getQuantity()).isEqualTo(3);
        assertThat(stock.getQuantity()).isGreaterThanOrEqualTo(0);

        ArgumentCaptor<StockMovement> movementCaptor = ArgumentCaptor.forClass(StockMovement.class);
        verify(repository).save(movementCaptor.capture());
        assertThat(movementCaptor.getValue().getType()).isEqualTo(StockMovementType.ISSUE);
        verify(lowStockRecommendationService).evaluateStockSafely(stock);

        ArgumentCaptor<StockIssueCommand> stockCommandCaptor = ArgumentCaptor.forClass(StockIssueCommand.class);
        verify(toirStockService).postDecrease(stockCommandCaptor.capture(), eq(StockLedgerMovementType.ISSUE));
        assertThat(stockCommandCaptor.getValue().quantity()).isEqualByComparingTo("5");
        assertThat(stockCommandCaptor.getValue().idempotencyKey()).startsWith("stock-movement-issue:");
    }

    @Test
    void receiptDoesNotTriggerLowStockEvaluation() {
        UUID warehouseId = UUID.randomUUID();
        UUID sparePartId = UUID.randomUUID();
        WarehouseStock stock = stock(warehouseId, sparePartId, 8, 0);

        when(stockRepository.findByWarehouseIdAndSparePartIdAndIsDeletedFalse(warehouseId, sparePartId))
                .thenReturn(Optional.of(stock));
        when(repository.save(any(StockMovement.class)))
                .thenAnswer(invocation -> saveWithId(invocation.getArgument(0)));

        service.create(request(warehouseId, sparePartId, StockMovementType.RECEIPT, 5));

        assertThat(stock.getQuantity()).isEqualTo(13);
        verify(lowStockRecommendationService, never()).evaluateStockSafely(any(WarehouseStock.class));

        ArgumentCaptor<StockReceiptCommand> stockCommandCaptor = ArgumentCaptor.forClass(StockReceiptCommand.class);
        verify(toirStockService).postIncrease(stockCommandCaptor.capture(), eq(StockLedgerMovementType.RECEIPT));
        assertThat(stockCommandCaptor.getValue().quantity()).isEqualByComparingTo("5");
        assertThat(stockCommandCaptor.getValue().idempotencyKey()).startsWith("stock-movement-receipt:");
    }

    @Test
    void receiptEndpointIncreasesStockAndStoresReceiptDocumentMetadata() {
        UUID warehouseId = UUID.randomUUID();
        UUID sparePartId = UUID.randomUUID();
        UUID responsiblePersonId = UUID.randomUUID();
        WarehouseStock stock = stock(warehouseId, sparePartId, 10, 0);
        LocalDate receivedAt = LocalDate.of(2026, 6, 13);

        when(stockRepository.findByWarehouseIdAndSparePartIdAndIsDeletedFalse(warehouseId, sparePartId))
                .thenReturn(Optional.of(stock));
        when(repository.save(any(StockMovement.class)))
                .thenAnswer(invocation -> saveWithId(invocation.getArgument(0)));

        StockMovementDto result = service.receipt(new StockMovementReceiptRequest(
                sparePartId,
                warehouseId,
                20,
                "LITER",
                BigDecimal.valueOf(45000),
                receivedAt,
                responsiblePersonId,
                "Local supplier",
                "PRX-2026-0001",
                "Motor moyi keldi"
        ));

        assertThat(stock.getQuantity()).isEqualTo(30);
        assertThat(result.type()).isEqualTo(StockMovementType.RECEIPT);
        assertThat(result.unit()).isEqualTo("LITER");
        assertThat(result.unitPrice()).isEqualByComparingTo("45000");
        assertThat(result.totalAmount()).isEqualByComparingTo("900000");
        assertThat(result.responsiblePersonId()).isEqualTo(responsiblePersonId);
        assertThat(result.supplierName()).isEqualTo("Local supplier");
        assertThat(result.movementDate()).isEqualTo(receivedAt);
        assertThat(result.comment()).isEqualTo("Motor moyi keldi");

        ArgumentCaptor<StockReceiptCommand> stockCommandCaptor = ArgumentCaptor.forClass(StockReceiptCommand.class);
        verify(toirStockService).postReceipt(stockCommandCaptor.capture());
        StockReceiptCommand stockCommand = stockCommandCaptor.getValue();
        assertThat(stockCommand.warehouseId()).isEqualTo(warehouseId);
        assertThat(stockCommand.sparePartId()).isEqualTo(sparePartId);
        assertThat(stockCommand.quantity()).isEqualByComparingTo("20");
        assertThat(stockCommand.unitCost()).isEqualByComparingTo("45000");
        assertThat(stockCommand.referenceType()).isEqualTo("STOCK_MOVEMENT");
        assertThat(stockCommand.referenceId()).isEqualTo(result.id());
        assertThat(stockCommand.referenceDocNo()).isEqualTo("PRX-2026-0001");
        assertThat(stockCommand.idempotencyKey()).isEqualTo("stock-movement-receipt:" + result.id());
    }

    @Test
    void genericReceiptWithProcurementDocumentNumberIsRejected() {
        UUID warehouseId = UUID.randomUUID();
        UUID sparePartId = UUID.randomUUID();
        StockMovementRequest request = new StockMovementRequest(
                warehouseId,
                sparePartId,
                null,
                StockMovementType.RECEIPT,
                5,
                null,
                "PR-2026-00005",
                null,
                "selected procurement receipt"
        );

        when(procurementRequestRepository.existsOpenReceivableByNumber("PR-2026-00005")).thenReturn(true);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("Use procurement receipt endpoint");

        verifyNoInteractions(stockRepository, repository, sparePartRepository);
    }

    @Test
    void issueEndpointDecreasesAvailableStockAndStoresRecipientMetadata() {
        UUID warehouseId = UUID.randomUUID();
        UUID sparePartId = UUID.randomUUID();
        UUID responsiblePersonId = UUID.randomUUID();
        UUID takenById = UUID.randomUUID();
        UUID workOrderId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        WarehouseStock stock = stock(warehouseId, sparePartId, 10, 2);
        LocalDate issuedAt = LocalDate.of(2026, 6, 13);

        when(stockRepository.findByWarehouseIdAndSparePartIdAndIsDeletedFalse(warehouseId, sparePartId))
                .thenReturn(Optional.of(stock));
        when(repository.save(any(StockMovement.class)))
                .thenAnswer(invocation -> saveWithId(invocation.getArgument(0)));

        StockMovementDto result = service.issue(new StockMovementIssueRequest(
                sparePartId,
                warehouseId,
                5,
                "LITER",
                issuedAt,
                takenById,
                responsiblePersonId,
                workOrderId,
                departmentId,
                "RSX-2026-0001",
                "Work order uchun moy berildi"
        ));

        assertThat(stock.getQuantity()).isEqualTo(5);
        assertThat(stock.getReservedQty()).isEqualTo(2);
        assertThat(result.type()).isEqualTo(StockMovementType.ISSUE);
        assertThat(result.unit()).isEqualTo("LITER");
        assertThat(result.takenById()).isEqualTo(takenById);
        assertThat(result.responsiblePersonId()).isEqualTo(responsiblePersonId);
        assertThat(result.workOrderId()).isEqualTo(workOrderId);
        assertThat(result.departmentId()).isEqualTo(departmentId);
        assertThat(result.movementDate()).isEqualTo(issuedAt);
        assertThat(result.comment()).isEqualTo("Work order uchun moy berildi");
        verify(lowStockRecommendationService).evaluateStockSafely(stock);

        ArgumentCaptor<StockIssueCommand> stockCommandCaptor = ArgumentCaptor.forClass(StockIssueCommand.class);
        verify(toirStockService).postIssue(stockCommandCaptor.capture());
        StockIssueCommand stockCommand = stockCommandCaptor.getValue();
        assertThat(stockCommand.warehouseId()).isEqualTo(warehouseId);
        assertThat(stockCommand.sparePartId()).isEqualTo(sparePartId);
        assertThat(stockCommand.quantity()).isEqualByComparingTo("5");
        assertThat(stockCommand.referenceType()).isEqualTo("STOCK_MOVEMENT");
        assertThat(stockCommand.referenceId()).isEqualTo(result.id());
        assertThat(stockCommand.referenceDocNo()).isEqualTo("RSX-2026-0001");
        assertThat(stockCommand.idempotencyKey()).isEqualTo("stock-movement-issue:" + result.id());
    }

    @Test
    void findAllPageReturnsRelationDisplayFieldsFromProjection() {
        UUID movementId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID sparePartId = UUID.randomUUID();
        UUID workOrderId = UUID.randomUUID();
        UUID createdById = UUID.randomUUID();
        Instant occurredAt = Instant.parse("2026-06-05T08:15:30Z");
        PageRequest pageRequest = PageRequest.of(0, 8);

        when(repository.findListRows(
                eq(true),
                eq(null),
                eq(null),
                eq(null),
                eq(null),
                eq(null),
                eq(null),
                eq(null),
                eq(null),
                eq(null),
                eq(pageRequest)))
                .thenReturn(new PageImpl<>(
                        List.of(stockMovementRow(
                                movementId,
                                warehouseId,
                                "Main Warehouse",
                                sparePartId,
                                "Bearing 6205",
                                workOrderId,
                                "WO-42",
                                "Pump repair",
                                createdById,
                                "Jane Smith",
                                occurredAt)),
                        pageRequest,
                        1));

        Page<StockMovementDto> result = service.findAll(0, 8);

        StockMovementDto dto = result.getContent().getFirst();
        assertThat(dto.id()).isEqualTo(movementId);
        assertThat(dto.warehouseId()).isEqualTo(warehouseId);
        assertThat(dto.warehouseName()).isEqualTo("Main Warehouse");
        assertThat(dto.sparePartId()).isEqualTo(sparePartId);
        assertThat(dto.sparePartName()).isEqualTo("Bearing 6205");
        assertThat(dto.sparePartType()).isEqualTo(SparePartType.BEARING);
        assertThat(dto.workOrderId()).isEqualTo(workOrderId);
        assertThat(dto.workOrderName()).isEqualTo("Pump repair");
        assertThat(dto.workOrderNumber()).isEqualTo("WO-42");
        assertThat(dto.createdById()).isEqualTo(createdById);
        assertThat(dto.createdByFullName()).isEqualTo("Jane Smith");
        assertThat(dto.sourceType()).isEqualTo(StockMovementSourceType.PROCUREMENT_REQUEST);
        assertThat(dto.sourceId()).isEqualTo(movementId);
        assertThat(dto.sourceLineId()).isEqualTo(sparePartId);
        assertThat(dto.occurredAt()).isEqualTo(occurredAt);
        assertThat(dto.fileCount()).isEqualTo(2);
    }

    @Test
    void attachFilesUploadsRepeatedFilesToReceiptMovement() {
        UUID movementId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID frontFileId = UUID.randomUUID();
        UUID invoiceFileId = UUID.randomUUID();
        StockMovement movement = movement(movementId, warehouseId, StockMovementType.RECEIPT);
        MockMultipartFile front = documentFile("invoice-front.pdf");
        MockMultipartFile invoice = documentFile("invoice.xlsx");
        UploadedFile frontFile = uploadedFile(frontFileId, userId, "invoice-front.pdf", "application/pdf", 100L);
        UploadedFile invoiceFile = uploadedFile(
                invoiceFileId,
                userId,
                "invoice.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                120L
        );

        when(repository.findByIdAndIsDeletedFalse(movementId)).thenReturn(Optional.of(movement));
        when(attachmentGroupService.listGroups(eq("STOCK_MOVEMENT"), eq(movementId), any())).thenReturn(List.of());
        when(attachmentGroupService.createGroup(eq("Stock movement documents"), any(), eq("STOCK_MOVEMENT"), eq(movementId),
                eq(List.of(front, invoice)), any(), any()))
                .thenReturn(stockMovementAttachmentGroup(movementId, List.of(frontFile, invoiceFile)));

        List<StockMovementFileDto> result = service.attachFiles(
                movementId,
                List.of(front, invoice),
                authenticatedUser(userId)
        );

        assertThat(result).extracting(StockMovementFileDto::id)
                .containsExactly(frontFileId, invoiceFileId);
        assertThat(result).extracting(StockMovementFileDto::originalName)
                .containsExactly("invoice-front.pdf", "invoice.xlsx");
        verify(attachmentGroupService).createGroup(eq("Stock movement documents"), any(), eq("STOCK_MOVEMENT"), eq(movementId),
                eq(List.of(front, invoice)), any(), any());
    }

    @Test
    void attachDocumentCreatesNamedGroupWithMultipleFiles() {
        UUID movementId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID frontFileId = UUID.randomUUID();
        UUID backFileId = UUID.randomUUID();
        StockMovement movement = movement(movementId, warehouseId, StockMovementType.RECEIPT);
        MockMultipartFile front = documentFile("invoice-front.pdf");
        MockMultipartFile back = documentFile("invoice-back.pdf");
        UploadedFile frontFile = uploadedFile(frontFileId, userId, "invoice-front.pdf", "application/pdf", 100L);
        UploadedFile backFile = uploadedFile(backFileId, userId, "invoice-back.pdf", "application/pdf", 100L);

        when(repository.findByIdAndIsDeletedFalse(movementId)).thenReturn(Optional.of(movement));
        when(attachmentGroupService.listGroups(eq("STOCK_MOVEMENT"), eq(movementId), any())).thenReturn(List.of());
        when(attachmentGroupService.createGroup(
                eq("Invoice"),
                any(),
                eq("STOCK_MOVEMENT"),
                eq(movementId),
                eq("RECEIPT_ACT"),
                eq("INV-2026-001"),
                eq(List.of(front, back)),
                any(),
                any()
        )).thenReturn(stockMovementAttachmentGroup(
                movementId,
                List.of(frontFile, backFile),
                "Invoice",
                "RECEIPT_ACT",
                "INV-2026-001"
        ));

        StockMovementDocumentDto result = service.attachDocument(
                movementId,
                List.of(front, back),
                " Invoice ",
                " RECEIPT_ACT ",
                " INV-2026-001 ",
                authenticatedUser(userId)
        );

        assertThat(result.documentName()).isEqualTo("Invoice");
        assertThat(result.documentType()).isEqualTo("RECEIPT_ACT");
        assertThat(result.documentNumber()).isEqualTo("INV-2026-001");
        assertThat(result.files()).extracting(StockMovementFileDto::id)
                .containsExactly(frontFileId, backFileId);
    }

    @Test
    void legacyAttachFilesReturnsNewFilesWhenMovementAlreadyHasMultipleDocumentGroups() {
        UUID movementId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID existingFileId = UUID.randomUUID();
        UUID otherDocumentFileId = UUID.randomUUID();
        UUID addedFileId = UUID.randomUUID();
        StockMovement movement = movement(movementId, warehouseId, StockMovementType.RECEIPT);
        MockMultipartFile added = documentFile("added.pdf");
        UploadedFile existingFile = uploadedFile(existingFileId, userId, "existing.pdf", "application/pdf", 100L);
        UploadedFile otherDocumentFile = uploadedFile(otherDocumentFileId, userId, "other.pdf", "application/pdf", 100L);
        UploadedFile addedFile = uploadedFile(addedFileId, userId, "added.pdf", "application/pdf", 100L);
        AttachmentGroupDto selectedGroup = stockMovementAttachmentGroup(movementId, List.of(existingFile));
        AttachmentGroupDto otherGroup = stockMovementAttachmentGroup(movementId, List.of(otherDocumentFile));

        when(repository.findByIdAndIsDeletedFalse(movementId)).thenReturn(Optional.of(movement));
        when(attachmentGroupService.listGroups(eq("STOCK_MOVEMENT"), eq(movementId), any()))
                .thenReturn(List.of(selectedGroup, otherGroup));
        when(attachmentGroupService.addFiles(eq(selectedGroup.id()), eq(List.of(added)), any(), any()))
                .thenReturn(stockMovementAttachmentGroup(movementId, List.of(existingFile, addedFile)));

        List<StockMovementFileDto> result = service.attachFiles(
                movementId,
                List.of(added),
                authenticatedUser(userId)
        );

        assertThat(result).extracting(StockMovementFileDto::id).containsExactly(addedFileId);
    }

    @Test
    void attachFilesRejectsTransferMovementBeforeUpload() {
        UUID movementId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        StockMovement movement = movement(movementId, warehouseId, StockMovementType.TRANSFER);

        when(repository.findByIdAndIsDeletedFalse(movementId)).thenReturn(Optional.of(movement));

        assertThatThrownBy(() -> service.attachFiles(
                movementId,
                List.of(documentFile("transfer.pdf")),
                authenticatedUser(userId)
        ))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("Stock movement files are supported only for RECEIPT and ISSUE");

        verifyNoInteractions(fileService);
    }

    @Test
    void findAllPassesMovementHistoryFiltersToRepository() {
        UUID warehouseId = UUID.randomUUID();
        UUID sparePartId = UUID.randomUUID();
        UUID responsiblePersonId = UUID.randomUUID();
        UUID workOrderId = UUID.randomUUID();
        LocalDate from = LocalDate.of(2026, 6, 1);
        LocalDate to = LocalDate.of(2026, 6, 13);
        PageRequest pageRequest = PageRequest.of(0, 8);

        when(repository.findListRows(
                eq(true),
                eq(null),
                eq(null),
                eq(StockMovementType.ISSUE.name()),
                eq(sparePartId),
                eq(warehouseId),
                eq(from),
                eq(to),
                eq(responsiblePersonId),
                eq(workOrderId),
                eq(pageRequest)))
                .thenReturn(Page.empty(pageRequest));

        service.findAll(0, 8, StockMovementType.ISSUE, sparePartId, warehouseId, from, to, responsiblePersonId, workOrderId);

        verify(repository).findListRows(
                eq(true),
                eq(null),
                eq(null),
                eq(StockMovementType.ISSUE.name()),
                eq(sparePartId),
                eq(warehouseId),
                eq(from),
                eq(to),
                eq(responsiblePersonId),
                eq(workOrderId),
                eq(pageRequest));
    }

    @Test
    void issueWithWorkOrderIdIsBlockedToPreserveMaterialUsageSourceOfTruth() {
        UUID warehouseId = UUID.randomUUID();
        UUID sparePartId = UUID.randomUUID();

        assertThatThrownBy(() -> service.create(requestWithWorkOrder(
                warehouseId,
                sparePartId,
                StockMovementType.ISSUE,
                1,
                UUID.randomUUID())))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("/api/v1/work-orders/{workOrderId}/material-usage");

        verifyNoInteractions(stockRepository, repository, sparePartRepository);
    }

    @Test
    void receiptWithWorkOrderIdIsBlockedToPreserveReceiptSourceOfTruth() {
        UUID warehouseId = UUID.randomUUID();
        UUID sparePartId = UUID.randomUUID();

        assertThatThrownBy(() -> service.create(requestWithWorkOrder(
                warehouseId,
                sparePartId,
                StockMovementType.RECEIPT,
                1,
                UUID.randomUUID())))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("Work order receipts");

        verifyNoInteractions(stockRepository, repository, sparePartRepository);
    }

    @Test
    void manualStockMovementRequiresReasonOrSourceDocument() {
        UUID warehouseId = UUID.randomUUID();
        UUID sparePartId = UUID.randomUUID();

        assertThatThrownBy(() -> service.create(new StockMovementRequest(
                warehouseId,
                sparePartId,
                null,
                StockMovementType.ADJUSTMENT,
                10,
                null,
                null,
                UUID.randomUUID(),
                " "
        )))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("reason or source document");

        verifyNoInteractions(stockRepository, repository, sparePartRepository);
    }

    private StockMovementRequest request(UUID warehouseId, UUID sparePartId, StockMovementType type, double quantity) {
        return requestWithWorkOrder(warehouseId, sparePartId, type, quantity, null);
    }

    private StockMovementRequest requestWithWorkOrder(
            UUID warehouseId,
            UUID sparePartId,
            StockMovementType type,
            double quantity,
            UUID workOrderId) {
        return new StockMovementRequest(
                warehouseId,
                sparePartId,
                workOrderId,
                type,
                quantity,
                null,
                "DOC-1",
                null,
                "Manual stock movement reason"
        );
    }

    private WarehouseStock stock(UUID warehouseId, UUID sparePartId, double quantity, double reservedQty) {
        WarehouseStock stock = new WarehouseStock();
        stock.setWarehouseId(warehouseId);
        stock.setSparePartId(sparePartId);
        stock.setQuantity(quantity);
        stock.setReservedQty(reservedQty);
        stock.setMinQty(0);
        return stock;
    }

    private Warehouse warehouse(UUID warehouseId) {
        Warehouse warehouse = new Warehouse();
        warehouse.setId(warehouseId);
        warehouse.setActive(true);
        return warehouse;
    }

    private StockMovement saveWithId(StockMovement movement) {
        ReflectionTestUtils.setField(movement, "id", UUID.randomUUID());
        return movement;
    }

    private StockMovementListRow stockMovementRow(
            UUID id,
            UUID warehouseId,
            String warehouseName,
            UUID sparePartId,
            String sparePartName,
            UUID workOrderId,
            String workOrderNumber,
            String workOrderName,
            UUID createdById,
            String createdByFullName,
            Instant occurredAt) {
        return new StockMovementListRow() {
            @Override
            public UUID getId() {
                return id;
            }

            @Override
            public UUID getWarehouseId() {
                return warehouseId;
            }

            @Override
            public String getWarehouseName() {
                return warehouseName;
            }

            @Override
            public UUID getSparePartId() {
                return sparePartId;
            }

            @Override
            public String getSparePartName() {
                return sparePartName;
            }

            @Override
            public String getSparePartType() {
                return SparePartType.BEARING.name();
            }

            @Override
            public UUID getWorkOrderId() {
                return workOrderId;
            }

            @Override
            public String getWorkOrderNumber() {
                return workOrderNumber;
            }

            @Override
            public String getWorkOrderName() {
                return workOrderName;
            }

            @Override
            public String getType() {
                return StockMovementType.RECEIPT.name();
            }

            @Override
            public double getQuantity() {
                return 2;
            }

            @Override
            public String getUnit() {
                return "PCS";
            }

            @Override
            public Double getUnitCost() {
                return 10.0;
            }

            @Override
            public BigDecimal getUnitPrice() {
                return BigDecimal.TEN;
            }

            @Override
            public BigDecimal getTotalAmount() {
                return BigDecimal.valueOf(20);
            }

            @Override
            public String getDocumentNumber() {
                return "DOC-1";
            }

            @Override
            public String getSourceType() {
                return StockMovementSourceType.PROCUREMENT_REQUEST.name();
            }

            @Override
            public UUID getSourceId() {
                return id;
            }

            @Override
            public UUID getSourceLineId() {
                return sparePartId;
            }

            @Override
            public UUID getCreatedById() {
                return createdById;
            }

            @Override
            public String getCreatedByFullName() {
                return createdByFullName;
            }

            @Override
            public UUID getResponsiblePersonId() {
                return createdById;
            }

            @Override
            public String getResponsiblePersonName() {
                return createdByFullName;
            }

            @Override
            public UUID getTakenById() {
                return null;
            }

            @Override
            public String getTakenByName() {
                return null;
            }

            @Override
            public UUID getDepartmentId() {
                return null;
            }

            @Override
            public String getSupplierName() {
                return null;
            }

            @Override
            public LocalDate getMovementDate() {
                return LocalDate.of(2026, 6, 5);
            }

            @Override
            public Instant getOccurredAt() {
                return occurredAt;
            }

            @Override
            public String getNotes() {
                return null;
            }

            @Override
            public String getComment() {
                return null;
            }

            @Override
            public long getFileCount() {
                return 2;
            }
        };
    }

    private StockMovement movement(UUID movementId, UUID warehouseId, StockMovementType type) {
        StockMovement movement = new StockMovement();
        ReflectionTestUtils.setField(movement, "id", movementId);
        movement.setWarehouseId(warehouseId);
        movement.setSparePartId(UUID.randomUUID());
        movement.setType(type);
        movement.setQuantity(1);
        return movement;
    }

    private MockMultipartFile documentFile(String originalName) {
        return new MockMultipartFile("files", originalName, "application/pdf", "%PDF-1.4\n".getBytes());
    }

    private UploadedFile uploadedFile(
            UUID id,
            UUID uploadedBy,
            String originalName,
            String contentType,
            long size
    ) {
        return UploadedFile.builder()
                .id(id)
                .originalName(originalName)
                .storedName(id + "-" + originalName)
                .objectName("stock-movement-documents/" + originalName)
                .contentType(contentType)
                .extension(originalName.substring(originalName.lastIndexOf('.') + 1))
                .size(size)
                .uploadedBy(uploadedBy)
                .category(FileCategory.STOCK_MOVEMENT_DOCUMENT)
                .deleted(false)
                .build();
    }

    private AttachmentGroupDto stockMovementAttachmentGroup(UUID movementId, List<UploadedFile> files) {
        return stockMovementAttachmentGroup(
                movementId,
                files,
                "Stock movement documents",
                null,
                null
        );
    }

    private AttachmentGroupDto stockMovementAttachmentGroup(
            UUID movementId,
            List<UploadedFile> files,
            String title,
            String documentType,
            String documentNumber
    ) {
        UUID groupId = UUID.randomUUID();
        return new AttachmentGroupDto(
                groupId,
                title,
                null,
                AttachmentTargetType.STOCK_MOVEMENT,
                movementId,
                documentType,
                documentNumber,
                files.getFirst().getUploadedBy(),
                java.time.LocalDateTime.now(),
                java.util.stream.IntStream.range(0, files.size())
                        .mapToObj(index -> {
                            UploadedFile file = files.get(index);
                            return new AttachmentGroupDto.FileItem(
                                    UUID.randomUUID(),
                                    file.getId(),
                                    file.getOriginalName(),
                                    file.getStoredName(),
                                    file.getContentType(),
                                    file.getSize(),
                                    index,
                                    null,
                                    file.getUploadedBy(),
                                    java.time.LocalDateTime.now(),
                                    "/download",
                                    "/presigned"
                            );
                        })
                        .toList()
        );
    }

    private UploadFileResponse uploadResponse(UploadedFile file) {
        return UploadFileResponse.builder()
                .id(file.getId())
                .originalName(file.getOriginalName())
                .storedName(file.getStoredName())
                .url(file.getUrl())
                .contentType(file.getContentType())
                .extension(file.getExtension())
                .size(file.getSize())
                .category(file.getCategory())
                .createdAt(file.getCreatedAt())
                .build();
    }

    private com.toir.security.AuthenticatedUser authenticatedUser(UUID userId) {
        return new com.toir.security.AuthenticatedUser(
                userId.toString(),
                "user",
                "user@example.com",
                "User",
                null,
                "USER",
                List.of()
        );
    }
}
