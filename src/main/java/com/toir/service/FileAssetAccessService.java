package com.toir.service;

import com.toir.entity.FileAsset;
import com.toir.enums.AttachmentTargetType;
import com.toir.security.AuthenticatedUser;
import com.toir.security.PermissionConstants;
import com.toir.service.attachment.AttachmentTargetAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FileAssetAccessService {

    private static final String SYSTEM_ADMIN = "SYSTEM_ADMIN";

    private final AttachmentTargetAccessService targetAccessService;

    public void assertCanAccess(FileAsset asset, AuthenticatedUser user) {
        if (asset == null) {
            throw new AccessDeniedException("Access denied by file scope");
        }
        UUID currentUserId = currentUserId(user);
        Optional<AttachmentTargetType> targetType = targetType(asset.getEntityType());
        Optional<UUID> targetId = parseUuid(asset.getEntityId());
        if (targetType.isPresent() && targetId.isPresent()) {
            targetAccessService.assertCanAccess(targetType.get(), targetId.get());
            return;
        }
        if (isSystemAdmin(user) || Objects.equals(asset.getUploadedById(), currentUserId)) {
            return;
        }
        throw new AccessDeniedException("Access denied by file owner scope");
    }

    public void assertCanAccessEntityReference(String entityType, String entityId, AuthenticatedUser user) {
        Optional<AttachmentTargetType> targetType = targetType(entityType);
        Optional<UUID> targetId = parseUuid(entityId);
        if (targetType.isPresent() && targetId.isPresent()) {
            targetAccessService.assertCanAccess(targetType.get(), targetId.get());
            return;
        }
        currentUserId(user);
    }

    public boolean canAccess(FileAsset asset, AuthenticatedUser user) {
        try {
            assertCanAccess(asset, user);
            return true;
        } catch (AccessDeniedException ex) {
            return false;
        }
    }

    private UUID currentUserId(AuthenticatedUser user) {
        if (user == null || !StringUtils.hasText(user.id())) {
            throw new AccessDeniedException("Access denied by file user scope");
        }
        return parseUuid(user.id())
                .orElseThrow(() -> new AccessDeniedException("Access denied by file user scope"));
    }

    private boolean isSystemAdmin(AuthenticatedUser user) {
        return user != null
                && (SYSTEM_ADMIN.equals(user.primaryRoleCode())
                || hasPermission(user, SYSTEM_ADMIN)
                || hasPermission(user, PermissionConstants.WILDCARD));
    }

    private boolean hasPermission(AuthenticatedUser user, String permission) {
        return user.permissions() != null && user.permissions().stream().anyMatch(permission::equals);
    }

    private Optional<AttachmentTargetType> targetType(String entityType) {
        if (!StringUtils.hasText(entityType)) {
            return Optional.empty();
        }
        return switch (normalize(entityType)) {
            case "equipment", "equipmentdocument", "equipmentwarranty", "technicaldocument" -> Optional.of(AttachmentTargetType.EQUIPMENT);
            case "vehicle", "vehicledocument" -> Optional.of(AttachmentTargetType.VEHICLE);
            case "workorder", "workorderdocument" -> Optional.of(AttachmentTargetType.WORK_ORDER);
            case "repairrequest", "repairrequestphoto" -> Optional.of(AttachmentTargetType.REPAIR_REQUEST);
            case "defect" -> Optional.of(AttachmentTargetType.DEFECT);
            case "completionact" -> Optional.of(AttachmentTargetType.COMPLETION_ACT);
            case "approval" -> Optional.of(AttachmentTargetType.APPROVAL);
            case "procurementrequest" -> Optional.of(AttachmentTargetType.PROCUREMENT_REQUEST);
            case "purchaseorder" -> Optional.of(AttachmentTargetType.PURCHASE_ORDER);
            case "stockmovement", "stockmovementdocument" -> Optional.of(AttachmentTargetType.STOCK_MOVEMENT);
            case "equipmentcommissioning" -> Optional.of(AttachmentTargetType.EQUIPMENT_COMMISSIONING);
            case "inventorycountsession" -> Optional.of(AttachmentTargetType.INVENTORY_COUNT_SESSION);
            case "warehousetask" -> Optional.of(AttachmentTargetType.WAREHOUSE_TASK);
            case "warehousebin" -> Optional.of(AttachmentTargetType.WAREHOUSE_BIN);
            case "warehousewriteoff" -> Optional.of(AttachmentTargetType.WAREHOUSE_WRITEOFF);
            case "hremployee", "employee", "employeepicture" -> Optional.of(AttachmentTargetType.HR_EMPLOYEE);
            default -> Optional.empty();
        };
    }

    private String normalize(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private Optional<UUID> parseUuid(String value) {
        if (!StringUtils.hasText(value)) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(value));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }
}
