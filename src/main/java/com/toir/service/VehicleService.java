package com.toir.service;

import com.toir.dto.equipment.EquipmentDto;
import com.toir.dto.vehicle.VehicleDetailDto;
import com.toir.dto.vehicle.VehicleRequest;
import com.toir.dto.vehicle.VehicleSummaryDto;
import com.toir.entity.Equipment;
import com.toir.entity.VehicleDetails;
import com.toir.enums.EquipmentCategory;
import com.toir.enums.EquipmentStatus;
import com.toir.exception.RestException;
import com.toir.repository.EquipmentRepository;
import com.toir.repository.VehicleDetailsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.Iterator;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class VehicleService {

    private final EquipmentRepository equipmentRepository;
    private final VehicleDetailsRepository vehicleDetailsRepository;
    private final EquipmentService equipmentService;

    @Transactional(readOnly = true)
    public Page<VehicleSummaryDto> list(UUID departmentId, EquipmentStatus status, String search, int page, int pageSize) {
        int safePage = Math.max(page, 1);
        int safePageSize = Math.max(pageSize, 1);
        Page<EquipmentDto> equipmentPage = equipmentService.search(
                departmentId,
                null,
                status,
                EquipmentCategory.VEHICLE,
                search,
                safePage - 1,
                safePageSize
        );
        Map<UUID, VehicleDetails> detailsByEquipment = vehicleDetailsRepository
                .findAllByEquipmentIdInAndIsDeletedFalse(equipmentPage.getContent().stream().map(EquipmentDto::id).toList())
                .stream()
                .collect(Collectors.toMap(VehicleDetails::getEquipmentId, Function.identity(), (a, b) -> a));
        List<VehicleSummaryDto> items = equipmentPage.getContent().stream()
                .map(equipment -> {
                    VehicleDetails details = detailsByEquipment.get(equipment.id());
                    return details == null ? null : VehicleSummaryDto.from(equipment, details);
                })
                .filter(Objects::nonNull)
                .toList();
        return new FixedTotalPage<>(items, equipmentPage.getPageable(), equipmentPage.getTotalElements());
    }

    @Transactional(readOnly = true)
    public VehicleDetailDto findByEquipmentId(UUID equipmentId) {
        EquipmentDto equipment = equipmentService.findById(equipmentId);
        if (equipment.category() != EquipmentCategory.VEHICLE) {
            throw RestException.badRequest("Equipment is not a vehicle: " + equipmentId);
        }
        VehicleDetails details = vehicleDetailsRepository.findByEquipmentIdAndIsDeletedFalse(equipmentId)
                .orElseThrow(() -> RestException.notFound("Vehicle details not found: " + equipmentId));
        return VehicleDetailDto.from(equipment, details);
    }

    @Transactional
    public VehicleDetailDto create(VehicleRequest request) {
        validateUniqueCreate(request);
        Equipment equipment = new Equipment();
        applyEquipment(equipment, request);
        Equipment savedEquipment = equipmentRepository.save(equipment);

        VehicleDetails details = new VehicleDetails();
        details.setEquipmentId(savedEquipment.getId());
        applyDetails(details, request);
        VehicleDetails savedDetails = vehicleDetailsRepository.save(details);

        return VehicleDetailDto.from(equipmentService.findById(savedEquipment.getId()), savedDetails);
    }

    @Transactional
    public VehicleDetailDto update(UUID equipmentId, VehicleRequest request) {
        Equipment equipment = equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)
                .orElseThrow(() -> RestException.notFound("Equipment not found: " + equipmentId));
        if (equipment.getCategory() != EquipmentCategory.VEHICLE) {
            throw RestException.badRequest("Equipment is not a vehicle: " + equipmentId);
        }
        VehicleDetails details = vehicleDetailsRepository.findByEquipmentIdAndIsDeletedFalse(equipmentId)
                .orElseThrow(() -> RestException.notFound("Vehicle details not found: " + equipmentId));

        validateUniqueUpdate(equipmentId, equipment, details, request);
        applyEquipment(equipment, request);
        applyDetails(details, request);
        return VehicleDetailDto.from(equipmentService.findById(equipmentId), details);
    }

    @Transactional
    public void delete(UUID equipmentId) {
        Equipment equipment = equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)
                .orElseThrow(() -> RestException.notFound("Equipment not found: " + equipmentId));
        VehicleDetails details = vehicleDetailsRepository.findByEquipmentIdAndIsDeletedFalse(equipmentId)
                .orElseThrow(() -> RestException.notFound("Vehicle details not found: " + equipmentId));
        details.setDeleted(true);
        equipment.setDeleted(true);
    }

    private void validateUniqueCreate(VehicleRequest request) {
        if (equipmentRepository.existsByCodeAndIsDeletedFalse(request.code())) {
            throw RestException.conflict("Equipment code already exists: " + request.code());
        }
        if (equipmentRepository.existsByInventoryNumberAndIsDeletedFalse(request.inventoryNumber())) {
            throw RestException.conflict("Inventory number already exists: " + request.inventoryNumber());
        }
        if (vehicleDetailsRepository.existsByPlateNumberAndIsDeletedFalse(request.plateNumber())) {
            throw RestException.conflict("Vehicle plate number already exists: " + request.plateNumber());
        }
        if (hasText(request.vin()) && vehicleDetailsRepository.existsByVinAndIsDeletedFalse(request.vin())) {
            throw RestException.conflict("Vehicle VIN already exists: " + request.vin());
        }
    }

    private void validateUniqueUpdate(UUID equipmentId, Equipment equipment, VehicleDetails details, VehicleRequest request) {
        if (!Objects.equals(equipment.getCode(), request.code())) {
            equipmentRepository.findByCodeAndIsDeletedFalse(request.code())
                    .filter(existing -> !Objects.equals(existing.getId(), equipmentId))
                    .ifPresent(existing -> {
                        throw RestException.conflict("Equipment code already exists: " + request.code());
                    });
        }
        if (!Objects.equals(equipment.getInventoryNumber(), request.inventoryNumber())) {
            equipmentRepository.findByInventoryNumberAndIsDeletedFalse(request.inventoryNumber())
                    .filter(existing -> !Objects.equals(existing.getId(), equipmentId))
                    .ifPresent(existing -> {
                        throw RestException.conflict("Inventory number already exists: " + request.inventoryNumber());
                    });
        }
        if (!Objects.equals(details.getPlateNumber(), request.plateNumber())) {
            vehicleDetailsRepository.findByPlateNumberAndIsDeletedFalse(request.plateNumber())
                    .filter(existing -> !Objects.equals(existing.getEquipmentId(), equipmentId))
                    .ifPresent(existing -> {
                        throw RestException.conflict("Vehicle plate number already exists: " + request.plateNumber());
                    });
        }

        String currentVin = normalizeBlankToNull(details.getVin());
        String requestedVin = normalizeBlankToNull(request.vin());
        if (requestedVin != null && !Objects.equals(currentVin, requestedVin)) {
            vehicleDetailsRepository.findByVinAndIsDeletedFalse(requestedVin)
                    .filter(existing -> !Objects.equals(existing.getEquipmentId(), equipmentId))
                    .ifPresent(existing -> {
                        throw RestException.conflict("Vehicle VIN already exists: " + requestedVin);
                    });
        }
    }

    private void applyEquipment(Equipment equipment, VehicleRequest request) {
        equipment.setCode(request.code());
        equipment.setName(request.name());
        equipment.setInventoryNumber(request.inventoryNumber());
        equipment.setTechnicalNumber(request.technicalNumber());
        equipment.setSerialNumber(request.serialNumber());
        equipment.setModel(request.model());
        equipment.setEquipmentTypeId(request.equipmentTypeId());
        equipment.setDepartmentId(request.departmentId());
        equipment.setLocationId(request.locationId());
        equipment.setStatus(request.status() != null ? request.status() : EquipmentStatus.ACTIVE);
        equipment.setCategory(EquipmentCategory.VEHICLE);
        equipment.setManufacturer(request.brand());
    }

    private void applyDetails(VehicleDetails details, VehicleRequest request) {
        if (request.currentOdometerKm() != null && request.currentOdometerKm() < 0) {
            throw RestException.badRequest("Current odometer cannot be negative");
        }
        if (request.currentEngineHours() != null && request.currentEngineHours() < 0) {
            throw RestException.badRequest("Current engine hours cannot be negative");
        }
        details.setPlateNumber(request.plateNumber());
        details.setVin(normalizeBlankToNull(request.vin()));
        details.setBrand(request.brand());
        details.setModel(request.model());
        details.setManufactureYear(request.manufactureYear());
        details.setVehicleType(request.vehicleType());
        details.setBodyNumber(request.bodyNumber());
        details.setChassisNumber(request.chassisNumber());
        details.setEngineNumber(request.engineNumber());
        details.setFuelType(request.fuelType());
        details.setFuelTankCapacity(request.fuelTankCapacity());
        details.setCarryingCapacity(request.carryingCapacity());
        details.setSeatCount(request.seatCount());
        details.setAssignedDriverId(request.assignedDriverId());
        details.setCurrentOdometerKm(request.currentOdometerKm() != null ? request.currentOdometerKm() : 0);
        details.setCurrentEngineHours(request.currentEngineHours() != null ? request.currentEngineHours() : 0);
        details.setRegistrationCertificateNumber(request.registrationCertificateNumber());
        details.setInsurancePolicyNumber(request.insurancePolicyNumber());
        details.setInsuranceExpiryDate(request.insuranceExpiryDate());
        details.setTechnicalInspectionExpiryDate(request.technicalInspectionExpiryDate());
        details.setGpsDeviceId(request.gpsDeviceId());
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String normalizeBlankToNull(String value) {
        return hasText(value) ? value : null;
    }

    private static final class FixedTotalPage<T> implements Page<T> {
        private final List<T> content;
        private final Pageable pageable;
        private final long totalElements;

        private FixedTotalPage(List<T> content, Pageable pageable, long totalElements) {
            this.content = content == null ? List.of() : List.copyOf(content);
            this.pageable = pageable == null ? Pageable.unpaged() : pageable;
            this.totalElements = totalElements;
        }

        @Override
        public int getTotalPages() {
            if (getSize() == 0) {
                return totalElements > 0 ? 1 : 0;
            }
            return (int) Math.ceil((double) totalElements / (double) getSize());
        }

        @Override
        public long getTotalElements() {
            return totalElements;
        }

        @Override
        public int getNumber() {
            return pageable.isPaged() ? pageable.getPageNumber() : 0;
        }

        @Override
        public int getSize() {
            return pageable.isPaged() ? pageable.getPageSize() : content.size();
        }

        @Override
        public int getNumberOfElements() {
            return content.size();
        }

        @Override
        public List<T> getContent() {
            return content;
        }

        @Override
        public boolean hasContent() {
            return !content.isEmpty();
        }

        @Override
        public Sort getSort() {
            return pageable.getSort();
        }

        @Override
        public boolean isFirst() {
            return !hasPrevious();
        }

        @Override
        public boolean isLast() {
            return !hasNext();
        }

        @Override
        public boolean hasNext() {
            return getNumber() + 1 < getTotalPages();
        }

        @Override
        public boolean hasPrevious() {
            return getNumber() > 0;
        }

        @Override
        public Pageable nextPageable() {
            return hasNext() ? pageable.next() : Pageable.unpaged();
        }

        @Override
        public Pageable previousPageable() {
            return hasPrevious() ? pageable.previousOrFirst() : Pageable.unpaged();
        }

        @Override
        public Iterator<T> iterator() {
            return Collections.unmodifiableList(content).iterator();
        }

        @Override
        public <U> Page<U> map(Function<? super T, ? extends U> converter) {
            List<U> mapped = new ArrayList<>(content.size());
            for (T item : content) {
                mapped.add(converter.apply(item));
            }
            return new FixedTotalPage<>(mapped, pageable, totalElements);
        }
    }
}
