package com.toir.service;

import com.toir.entity.FileAsset;
import com.toir.enums.AttachmentTargetType;
import com.toir.security.AuthenticatedUser;
import com.toir.service.attachment.AttachmentTargetAccessService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class FileAssetAccessServiceTest {

    @Mock
    AttachmentTargetAccessService targetAccessService;

    @Test
    void knownEntityFileUsesTargetAccessInsteadOfUploaderOnly() {
        UUID requestId = UUID.randomUUID();
        FileAsset file = file("RepairRequest", requestId.toString(), UUID.randomUUID());
        FileAssetAccessService service = new FileAssetAccessService(targetAccessService);

        service.assertCanAccess(file, user(UUID.randomUUID(), "VIEWER", List.of()));

        verify(targetAccessService).assertCanAccess(AttachmentTargetType.REPAIR_REQUEST, requestId);
    }

    @Test
    void miscFileAllowsUploader() {
        UUID uploaderId = UUID.randomUUID();
        FileAsset file = file("misc", "", uploaderId);
        FileAssetAccessService service = new FileAssetAccessService(targetAccessService);

        service.assertCanAccess(file, user(uploaderId, "VIEWER", List.of()));
    }

    @Test
    void miscFileRejectsNonUploader() {
        FileAsset file = file("misc", "", UUID.randomUUID());
        FileAssetAccessService service = new FileAssetAccessService(targetAccessService);

        assertThatThrownBy(() -> service.assertCanAccess(file, user(UUID.randomUUID(), "VIEWER", List.of())))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("Access denied by file owner scope");
    }

    @Test
    void visibleFilterSuppressesInaccessibleFilesForGenericLegacyList() {
        UUID uploaderId = UUID.randomUUID();
        FileAsset own = file("misc", "", uploaderId);
        FileAsset other = file("misc", "", UUID.randomUUID());
        FileAssetAccessService service = new FileAssetAccessService(targetAccessService);
        AuthenticatedUser user = user(uploaderId, "VIEWER", List.of());

        assertThat(service.canAccess(own, user)).isTrue();
        assertThat(service.canAccess(other, user)).isFalse();
    }

    private FileAsset file(String entityType, String entityId, UUID uploaderId) {
        FileAsset file = new FileAsset();
        file.setId(UUID.randomUUID());
        file.setEntityType(entityType);
        file.setEntityId(entityId);
        file.setUploadedById(uploaderId);
        return file;
    }

    private AuthenticatedUser user(UUID userId, String role, List<String> permissions) {
        return new AuthenticatedUser(userId.toString(), "user", "user@example.com", "User", null, role, permissions);
    }
}
