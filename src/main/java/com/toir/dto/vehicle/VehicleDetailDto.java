package com.toir.dto.vehicle;

import com.toir.dto.equipment.EquipmentDto;
import com.toir.dto.equipmentmanualattribute.EquipmentManualAttributeDto;
import com.toir.entity.UploadedFile;
import com.toir.entity.equipment.VehicleDocument;
import com.toir.entity.equipment.VehicleDetails;
import com.toir.enums.VehicleType;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record VehicleDetailDto(
        EquipmentDto equipment,
        Details vehicleDetails,
        List<EquipmentManualAttributeDto> manualAttributes
) {
    public VehicleDetailDto(EquipmentDto equipment, Details vehicleDetails) {
        this(equipment, vehicleDetails, List.of());
    }

    public record Details(
            UUID id,
            String plateNumber,
            String vin,
            String brand,
            String model,
            Integer manufactureYear,
            VehicleType vehicleType,
            String bodyNumber,
            String chassisNumber,
            String engineNumber,
            String fuelType,
            Double fuelTankCapacity,
            Double carryingCapacity,
            Integer seatCount,
            UUID assignedDriverId,
            double currentOdometerKm,
            double currentEngineHours,
            String registrationCertificateNumber,
            String insurancePolicyNumber,
            LocalDate insuranceExpiryDate,
            LocalDate technicalInspectionExpiryDate,
            String gpsDeviceId,
            DocumentRef document,
            List<VehicleDocumentDto> documents
    ) {
    }

    public record DocumentRef(
            UUID id,
            String originalName,
            String contentType,
            Long size,
            String downloadUrl,
            String presignedUrlEndpoint
    ) {
        public static DocumentRef from(UploadedFile file) {
            if (file == null || Boolean.TRUE.equals(file.getDeleted())) {
                return null;
            }
            return new DocumentRef(
                    file.getId(),
                    file.getOriginalName(),
                    file.getContentType(),
                    file.getSize(),
                    "/api/files/" + file.getId() + "/download",
                    "/api/files/" + file.getId() + "/presigned-url"
            );
        }
    }

    public static VehicleDetailDto from(EquipmentDto equipment, VehicleDetails details) {
        return from(equipment, details, List.of(), List.of());
    }

    public static VehicleDetailDto from(EquipmentDto equipment, VehicleDetails details, List<VehicleDocument> documents) {
        return from(equipment, details, documents, List.of());
    }

    public static VehicleDetailDto from(
            EquipmentDto equipment,
            VehicleDetails details,
            List<VehicleDocument> documents,
            List<EquipmentManualAttributeDto> manualAttributes
    ) {
        List<VehicleDocumentDto> documentDtos = documents == null ? List.of() : documents.stream()
                .map(document -> VehicleDocumentDto.from(details.getEquipmentId(), document))
                .filter(dto -> dto != null)
                .toList();
        DocumentRef legacyDocument = documentDtos.isEmpty()
                ? DocumentRef.from(details.getDocumentFile())
                : toDocumentRef(documentDtos.getFirst());
        return new VehicleDetailDto(
                equipment,
                new Details(
                        details.getId(),
                        details.getPlateNumber(),
                        details.getVin(),
                        details.getBrand(),
                        details.getModel(),
                        details.getManufactureYear(),
                        details.getVehicleType(),
                        details.getBodyNumber(),
                        details.getChassisNumber(),
                        details.getEngineNumber(),
                        details.getFuelType(),
                        details.getFuelTankCapacity(),
                        details.getCarryingCapacity(),
                        details.getSeatCount(),
                        details.getAssignedDriverId(),
                        details.getCurrentOdometerKm(),
                        details.getCurrentEngineHours(),
                        details.getRegistrationCertificateNumber(),
                        details.getInsurancePolicyNumber(),
                        details.getInsuranceExpiryDate(),
                        details.getTechnicalInspectionExpiryDate(),
                        details.getGpsDeviceId(),
                        legacyDocument,
                        documentDtos
                ),
                manualAttributes == null ? List.of() : List.copyOf(manualAttributes)
        );
    }

    private static DocumentRef toDocumentRef(VehicleDocumentDto document) {
        if (document == null) {
            return null;
        }
        return new DocumentRef(
                document.id(),
                document.originalName(),
                document.contentType(),
                document.size(),
                document.downloadUrl(),
                document.presignedUrlEndpoint()
        );
    }
}
